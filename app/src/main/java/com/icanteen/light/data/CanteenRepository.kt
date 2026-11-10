package com.icanteen.light.data

import android.util.Log
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.ConcurrentHashMap

class CanteenRepository(baseUrl: String = "https://stravovani.sspbrno.cz") {

    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    private val client = OkHttpClient.Builder()
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                val existing = cookieStore[url.host] ?: mutableListOf()
                // Replace existing cookies with new ones if they have the same name
                val newCookies = cookies.toMutableList()
                existing.removeAll { old -> newCookies.any { it.name == old.name } }
                existing.addAll(newCookies)
                cookieStore[url.host] = existing
            }
            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return cookieStore[url.host] ?: mutableListOf()
            }
        })
        .followRedirects(true)
        .build()

    private val BASE_URL = baseUrl.trim().removeSuffix("/")
    private val LOGIN_URL = "$BASE_URL/faces/login.jsp"
    private val POST_URL = "$BASE_URL/j_spring_security_check"
    private val MAIN_URL = "$BASE_URL/faces/secured/month.jsp?terminal=false&keyboard=false&printer=false"

    fun login(user: String, pass: String): Boolean {
        cookieStore.clear()
        
        return try {
            val getReq = Request.Builder().url(LOGIN_URL).build()
            val loginHtml = client.newCall(getReq).execute().use { it.body?.string() ?: "" }

            val csrfTokenMatch = Regex("name=\"_csrf\"\\s+value=\"([^\"]+)\"").find(loginHtml)
            val csrfToken = csrfTokenMatch?.groupValues?.get(1) ?: ""

            val formBodyBuilder = FormBody.Builder()
                .add("j_username", user)
                .add("j_password", pass)
                .add("terminal", "false")
                .add("targetUrl", "/faces/secured/main.jsp?terminal=false&status=true&printer=&keyboard=")
            
            if (csrfToken.isNotEmpty()) {
                formBodyBuilder.add("_csrf", csrfToken)
            }
                
            val postReq = Request.Builder()
                .url(POST_URL)
                .post(formBodyBuilder.build())
                .build()
                
            client.newCall(postReq).execute().use { response ->
                val finalUrl = response.request.url.toString()
                // iCanteen redirects to main.jsp on success, or back to login.jsp?error=true on failure
                if (finalUrl.contains("error", ignoreCase = true) || finalUrl.contains("login.jsp", ignoreCase = true)) {
                    false
                } else {
                    response.isSuccessful
                }
            }
        } catch (e: Exception) {
            Log.e("CanteenRepo", "Login failed", e)
            false
        }
    }

    fun fetchMenuHtmlRaw(): String? {
        return try {
            // Replicate browser flow: initialize JSF session view on main.jsp
            val mainReq = Request.Builder()
                .url("$BASE_URL/faces/secured/main.jsp?terminal=false&status=true&printer=&keyboard=")
                .header("Referer", LOGIN_URL)
                .build()
            client.newCall(mainReq).execute().close()

            // Fetch month.jsp
            val request = Request.Builder()
                .url(MAIN_URL)
                .header("Referer", "$BASE_URL/faces/secured/main.jsp")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()
            }
        } catch (e: Exception) {
            Log.e("CanteenRepo", "Fetch menu failed", e)
            null
        }
    }

    fun fetchMenu(): MenuData? {
        val html = fetchMenuHtmlRaw() ?: return null
        return parseMenuHtml(html)
    }

    private fun parseMenuHtml(html: String): MenuData {
        val result = mutableListOf<DayMenu>()
        var userName = "Neznámý uživatel"
        var userCredit = "? Kč"
        
        try {
            val document = Jsoup.parse(html)
            
            // Extract user info
            val userElem = document.select("#hlavicka_user, .loggedUser, #loggedUser, td.statusBarRight").first()
            if (userElem != null) userName = userElem.text().replace("Uživatel:", "").trim()
            
            val creditElems = document.select("#hlavicka_kredit, .kredit, #kredit, td.statusBarMain")
            for (elem in creditElems) {
                val text = elem.text()
                if (text.contains("kredit:", ignoreCase = true)) {
                    userCredit = text.substringAfter("kredit:").trim()
                    break
                } else if (elem.id() == "hlavicka_kredit" || elem.hasClass("kredit")) {
                    userCredit = text.replace("Konto:", "", ignoreCase = true).replace("Kredit:", "", ignoreCase = true).replace("Kredit", "", ignoreCase = true).trim()
                    break
                }
            }

            // Look for day headers
            val dayHeaders = document.select(".jidelnicekTop")
            
            for (headerElem in dayHeaders) {
                val headerText = headerElem.text().trim()
                
                // Format: "Jídelníček na 08.04.2026 - Středa"
                val dateMatch = Regex("(\\d{2}\\.\\d{2}\\.\\d{4})").find(headerText)
                val dateStr = dateMatch?.value ?: headerText
                val dayName = headerText.split("-").lastOrNull()?.trim() ?: ""
                
                // The structure has form -> jidelnicekTop + orderContent
                var orderContent = headerElem.nextElementSibling()
                if (orderContent == null || !orderContent.hasClass("orderContent")) {
                    // Fallback to parent if structure differs slightly
                    orderContent = headerElem.parent()
                }
                if (orderContent == null) continue
                
                val mealElements = orderContent.select(".jidelnicekItem")
                val dayMeals = mutableListOf<LunchItem>()
                
                for (item in mealElements) {
                    val titleElem = item.select(".smallBoldTitle").first() ?: continue
                    val mealFullTitle = titleElem.text().trim()
                    
                    if (!mealFullTitle.contains("Oběd", ignoreCase = true) && !mealFullTitle.contains("Menu", ignoreCase = true)) continue
                    val mealNumber = mealFullTitle.replace("Oběd", "", ignoreCase = true).replace("Menu", "", ignoreCase = true).trim()
                    
                    val contentElem = item.select(".jidWrapCenter").first() ?: item
                    val mealName = cleanMealName(contentElem.text())
                    
                    if (mealName.isEmpty()) continue
                    
                    val status = determineStatus(item)
                    val btnElem = item.select("[onclick*='ajaxOrder'], [onclick*='dbProcessOrder'], [href*='dbProcessOrder'], a.btn, button.btn").first()
                        ?: item.select("a, button, span, div").find { it.attr("onclick").contains("ajaxOrder") || it.attr("onclick").contains("dbProcessOrder") || it.attr("href").contains("dbProcessOrder") }

                    val rawOnclick = btnElem?.attr("onclick") ?: ""
                    val rawHref = btnElem?.attr("href") ?: ""
                    val itemHtml = item.html()

                    val orderCommand = when {
                        rawOnclick.isNotEmpty() -> extractOrderPath(rawOnclick)
                        rawHref.contains("dbProcessOrder") -> extractOrderPath(rawHref)
                        else -> {
                            val match = Regex("""ajaxOrder\s*\([^,]*,\s*['"]([^'"]+)['"]""").find(itemHtml)
                                ?: Regex("""['"]([^'"]*db/dbProcessOrder\.jsp[^'"]*)['"]""").find(itemHtml)
                                ?: Regex("""(?<=ajaxOrder\(this, ')[^']+(?=')""").find(itemHtml)
                            match?.groupValues?.getOrNull(1)?.replace("&amp;", "&")?.replace("amp;", "&")?.trim()
                                ?: match?.value?.replace("&amp;", "&")?.replace("amp;", "&")?.trim()
                        }
                    }
                    
                    dayMeals.add(LunchItem(
                        mealNumber = mealNumber,
                        mealName = mealName,
                        status = status,
                        orderCommand = orderCommand
                    ))
                }
                
                if (dayMeals.isNotEmpty()) {
                    result.add(DayMenu(dayName, dateStr, dayMeals.toList()))
                }
            }
            
        } catch (e: Exception) {
            Log.e("CanteenRepo", "Error parsing HTML", e)
        }
        return MenuData(UserInfo(userName, userCredit), result)
    }

    private fun cleanMealName(raw: String): String {
        return raw.replace(Regex("\\(\\s*\\d+(,\\s*\\d+)*\\s*\\)"), "") // Remove allergens like (1, 3, 7)
            .replace(Regex("alergeny:.*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .removeSuffix(",")
            .trim()
    }

    private fun determineStatus(element: org.jsoup.nodes.Element): OrderStatus {
        val btn = element.select("[onclick*='ajaxOrder'], [onclick*='dbProcessOrder'], a.btn, button.btn").first()
            ?: element.select("a, button, span").first()
        val btnText = btn?.text()?.lowercase() ?: ""
        val text = element.text().lowercase()
        val html = element.html().lowercase()
        
        // Priority 1: Served (Vydáno)
        if (text.contains("vydáno") || element.select(".icon-vydano").isNotEmpty()) {
            return OrderStatus.SERVED
        }
        
        // Ordered meals have a checkmark icon or "zrušit" text/button class
        val hasCheck = element.select(".fa-check, .icon-ok, .jidelnicekItem-ordered").isNotEmpty() || btn?.hasClass("ordered") == true
        val isOrdered = hasCheck || btnText.contains("zrušit") || text.contains("máte objednáno")
        
        if (isOrdered) {
            // Cannot cancel anymore
            if (btnText.contains("nelze") || text.contains("nelze zrušit") || text.contains("nelze změnit") || btn?.hasClass("disabled") == true) {
                return OrderStatus.ORDERED_LOCKED
            }
            return OrderStatus.ORDERED
        } else {
            // Not ordered
            if (btnText.contains("nelze") || text.contains("nelze objednat") || text.contains("expirovalo") || text.contains("uzavřeno") || btn?.hasClass("disabled") == true) {
                return OrderStatus.LOCKED
            } else if (btnText.contains("objednat") || btnText.contains("přeobjednat") || btn?.hasClass("enabled") == true || html.contains("ajaxorder") || (btn != null && btn.attr("onclick").contains("ajaxOrder"))) {
                return OrderStatus.AVAILABLE
            }
        }
        
        return OrderStatus.NOT_AVAILABLE
    }

    fun extractOrderPath(raw: String): String {
        var s = raw.trim()
        val match = Regex("""ajaxOrder\s*\([^,]*,\s*['"]([^'"]+)['"]""").find(s)
        if (match != null) {
            s = match.groupValues[1]
        } else {
            val quoteMatch = Regex("""['"]([^'"]*db/dbProcessOrder\.jsp[^'"]*)['"]""").find(s)
            if (quoteMatch != null) {
                s = quoteMatch.groupValues[1]
            }
        }
        return s.replace("&amp;", "&").replace("amp;", "&").trim()
    }

    fun changeOrder(
        dateStr: String,
        mealNumber: String,
        user: String,
        pass: String,
        isCancel: Boolean
    ): Result<MenuData> {
        return try {
            // Step 1: Login fresh session
            if (!login(user, pass)) {
                return Result.failure(Exception("Přihlášení do iCanteen selhalo. Zkontroluj přihlašovací údaje v Nastavení."))
            }

            // Step 2: Initialize JSF session and get LIVE month page HTML
            val monthHtml = fetchMenuHtmlRaw()
                ?: return Result.failure(Exception("Nepodařilo se načíst aktuální data z iCanteen."))

            // Step 3: Find the live meal item in this exact session
            val liveMenuData = parseMenuHtml(monthHtml)
            val cleanDate = dateStr.trim()
            val targetDay = liveMenuData.days.find { 
                it.dateStr.trim() == cleanDate || it.dateStr.contains(cleanDate) || cleanDate.contains(it.dateStr.trim())
            } ?: return Result.failure(Exception("Den $cleanDate nebyl v jídelníčku nalezen."))

            val cleanNum = mealNumber.trim()
            val targetMeal = targetDay.meals.find { it.mealNumber.trim() == cleanNum }
                ?: return Result.failure(Exception("Oběd č. $cleanNum nebyl pro den $cleanDate nalezen."))

            val rawCommand = targetMeal.orderCommand
            if (rawCommand.isNullOrBlank()) {
                return Result.failure(Exception("Jídelna v tento moment nepovoluje změnu tohoto oběda."))
            }

            val targetPath = extractOrderPath(rawCommand)
            if (targetPath.isBlank()) {
                return Result.failure(Exception("Neplatný odkaz pro objednání."))
            }

            val fullUrl = when {
                targetPath.startsWith("http://") || targetPath.startsWith("https://") -> targetPath
                targetPath.startsWith("/faces/") -> "$BASE_URL$targetPath"
                targetPath.startsWith("faces/") -> "$BASE_URL/$targetPath"
                targetPath.startsWith("/") -> "$BASE_URL/faces/secured$targetPath"
                else -> "$BASE_URL/faces/secured/$targetPath"
            }

            Log.d("CanteenRepo", "Executing order: $fullUrl (isCancel=$isCancel)")

            // Step 4: Execute the AJAX GET request
            val orderReq = Request.Builder()
                .url(fullUrl)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Accept", "*/*")
                .header("Referer", MAIN_URL)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                .build()

            val responseBody = client.newCall(orderReq).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(Exception("Server iCanteen vrátil kód ${response.code}."))
                }
                response.body?.string() ?: ""
            }

            Log.d("CanteenRepo", "Order response body: $responseBody")

            val lowerBody = responseBody.lowercase()
            if (lowerBody.contains("přihlášení uživatele") || lowerBody.contains("login.jsp")) {
                return Result.failure(Exception("Vypršela platnost přihlášení."))
            }
            if (lowerBody.contains("error") || lowerBody.contains("fail") || (lowerBody.contains("chyba") && !lowerBody.contains("bez chyb"))) {
                val cleanErr = Jsoup.parse(responseBody).text().take(120).trim()
                val msg = if (cleanErr.isNotBlank() && cleanErr.length > 5) cleanErr else "Jídelna objednávku odmítla (např. uzávěrka nebo nedostatek kreditu)."
                return Result.failure(Exception(msg))
            }

            // Step 5: Fetch updated menu to verify change on server
            val updatedHtml = fetchMenuHtmlRaw()
                ?: return Result.failure(Exception("Objednávka byla odeslána, ale nepodařilo se ověřit stav."))
            val updatedMenu = parseMenuHtml(updatedHtml)

            val updatedDay = updatedMenu.days.find { 
                it.dateStr.trim() == cleanDate || it.dateStr.contains(cleanDate) || cleanDate.contains(it.dateStr.trim())
            }
            val updatedMeal = updatedDay?.meals?.find { it.mealNumber.trim() == cleanNum }

            if (isCancel) {
                if (updatedMeal?.isOrdered == true) {
                    return Result.failure(Exception("Jídelna oběd neodhlásila. Zkontrolujte stav přímo v iCanteen."))
                }
            } else {
                if (updatedMeal?.isOrdered != true) {
                    return Result.failure(Exception("Jídelna změnu nepotvrdila (zkontrolujte kredit nebo termín)."))
                }
            }

            Result.success(updatedMenu)
        } catch (e: Exception) {
            Log.e("CanteenRepo", "changeOrder failed", e)
            Result.failure(e)
        }
    }
}
