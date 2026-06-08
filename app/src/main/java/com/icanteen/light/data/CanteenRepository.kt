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

    fun fetchMenu(): MenuData? {
        return try {
            val request = Request.Builder().url(MAIN_URL).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val html = response.body?.string() ?: return null
                parseMenuHtml(html)
            }
        } catch (e: Exception) {
            Log.e("CanteenRepo", "Fetch menu failed", e)
            null
        }
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
                    val btnElem = item.select("a.btn").first()
                    val orderCommand = btnElem?.attr("onclick")
                    
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
        val btn = element.select("a.btn").first()
        val btnText = btn?.text()?.lowercase() ?: ""
        val text = element.text().lowercase()
        
        // Priority 1: Served (Vydáno)
        if (text.contains("vydáno") || element.select(".icon-vydano").isNotEmpty()) {
            return OrderStatus.SERVED
        }
        
        // Ordered meals have a checkmark icon or "zrušit" text/button class
        val hasCheck = element.select(".fa-check, .icon-ok, .jidelnicekItem-ordered").isNotEmpty() || btn?.hasClass("ordered") == true
        val isOrdered = hasCheck || btnText.contains("zrušit")
        
        if (isOrdered) {
            // Cannot cancel anymore
            if (btnText.contains("nelze") || text.contains("nelze") || btn?.hasClass("disabled") == true) {
                return OrderStatus.ORDERED_LOCKED
            }
            return OrderStatus.ORDERED
        } else {
            // Not ordered
            if (btnText.contains("nelze") || text.contains("nelze") || text.contains("expirovalo") || text.contains("uzavřeno") || btn?.hasClass("disabled") == true) {
                return OrderStatus.LOCKED
            } else if (btnText.contains("objednat") || btnText.contains("přeobjednat") || btn?.hasClass("enabled") == true || btn != null) {
                return OrderStatus.AVAILABLE
            }
        }
        
        return OrderStatus.NOT_AVAILABLE
    }
}
