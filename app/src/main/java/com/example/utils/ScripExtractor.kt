package com.example.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URL

data class KotakStockInfo(
    val symbol: String,
    val companyName: String,
    val series: String,
    val exchange: String,
    val mtfKotak: Int,
    val researchKotak: Int
)

object ScripExtractor {

    // Top 20 NSE Stocks validation list to avoid false positives (standard ticker names)
    val TOP_20_NSE_STOCKS = setOf(
        "RELIANCE", 
        "TCS", 
        "HDFCBANK", 
        "BHARTIARTL", 
        "ICICIBANK", 
        "INFY", 
        "SBIN", 
        "SBI", 
        "LICI", 
        "LIC", 
        "ITC", 
        "HINDUNILVR", 
        "LT", 
        "HCLTECH", 
        "BAJFINANCE", 
        "SUNPHARMA", 
        "MARUTI",
        "ADANIENT", 
        "NTPC", 
        "TATAMOTORS", 
        "ONGC", 
        "AXISBANK"
    )

    val DERIVATIVE_INDICES = setOf(
        "NIFTY", "BANKNIFTY", "FINNIFTY", "MIDCPNIFTY", "SENSEX", "BANKEX", "INDIAVIX"
    )

    val COMMODITIES = setOf(
        "GOLD", "GOLDM", "SILVER", "SILVERM", "SILVERSING", "CRUDEOIL", "NATURALGAS", "COPPER", "NICKEL", "LEAD", "ZINC", "ALUMINIUM", "MENTHAOIL", "COTTON"
    )

    // Complete set of all available scrip symbols for validation, searching, etc. using Kotak Master Stocks list
    val ALL_INDIAN_MARKET_SCRIPS: Set<String> = (
        DERIVATIVE_INDICES + 
        COMMODITIES + 
        kotakStockList.map { it.symbol } +
        listOf("SBI", "LIC", "M_M", "BAJAJAUTO", "RAMCOCEM", "ZOMATO")
    ).toSet()

    // Common words that can also double as stock symbols or ticker names
    val AMBIGUOUS_WORDS = setOf(
        "IT", "AND", "GO", "CAN", "BEST", "LINE", "NEW", "CARE", "KEY", "FINE", 
        "WIND", "GOLD", "OIL", "LEAD", "ZINC", "COPPER", "IDEA", "SAIL", "COAL", 
        "POWER", "SHREE", "CROWN", "PRIDE", "GRACE", "FOCUS", "SIGN", "EXCEL", 
        "APEX", "CREATIVE", "GIFT", "MIND", "WAVE", "YES", "UCO", "UNION", "SOUTH",
        "CENTRAL", "FEDERAL", "INDIAN", "TAMIL", "KARNATAKA", "KERALA", "MAHARASHTRA",
        "PUNJAB", "GUJARAT", "CABLE", "WIRE", "STAR", "GLOBAL", "MEDIA", "TECH",
        "COTTON", "BANG", "JUST", "BRAND", "FUTURE", "CONSOLIDATED"
    )

    // Financial & stock-market related contextual keywords
    val FINANCIAL_KEYWORDS = setOf(
        "stock", "share", "price", "cmp", "buy", "sell", "hold", "target", "market", 
        "nse", "bse", "nifty", "sensex", "trade", "trading", "invest", "investment", 
        "portfolio", "dividend", "yield", "profit", "loss", "revenue", "earnings", 
        "crore", "cr", "rs", "results", "quarterly", "eps", "pe", "valuation", 
        "bullish", "bearish", "derivatives", "futures", "options", "call", "put", 
        "mcx", "allotment", "ipo", "listing", "broker", "brokerage", "securities",
        "advisor", "advisory", "research", "chart", "trend", "volume", "leverage"
    )

    // Comprehensive set of common, generic, or UI words that cannot be used as standalone first-word stock matches
    val COMMON_OR_GENERIC_WORDS = setOf(
        "bharat", "india", "indian", "global", "national", "international", "associated", 
        "united", "standard", "general", "central", "state", "apex", "shree", "shri", 
        "sri", "dr", "the", "asian", "hindustan", "eastern", "western", "northern", 
        "southern", "royal", "premier", "supreme", "universal", "classic", "excellent", 
        "perfect", "prime", "mega", "micro", "macro", "multi", "omni", "super", "ultra", 
        "dynamic", "creative", "smart", "fine", "best", "good", "new", "total", "power", 
        "energy", "industries", "industry", "limited", "ltd", "corporation", "corp", 
        "holding", "holdings", "group", "ventures", "venture", "associates", "partners", 
        "solutions", "services", "technologies", "technology", "systems", "system", 
        "capital", "finance", "financial", "securities", "investments", "investment",
        "consolidated", "future", "futures", "just", "excel", "brand", "bang", "construction", 
        "enterprise", "enterprises", "market", "markets", "network", "networks", "concept", 
        "concepts", "overseas", "cotton", "home", "search", "portfolio", "watchlist", "orders",
        "account", "profile", "discover", "news", "settings", "help", "back", "next", "close",
        "done", "edit", "delete", "view", "show", "hide", "more", "less", "buy", "sell", "trade",
        "stock", "stocks", "share", "shares", "price", "prices", "index", "indices",
        "limited", "ltd", "corp", "corporation", "company", "companies", "plc", "inc",
        "green", "clean", "solar", "wind", "infra", "infrastructure", "heavy", "electrical",
        "electricals", "chemical", "chemicals", "pharma", "pharmaceuticals", "steel", "iron",
        "metal", "metals", "mining", "mines", "paper", "textiles", "textile", "sugar", "tea",
        "coffee", "cement", "ceramic", "ceramics", "glass", "telecom", "software", "digital",
        "hospitality", "holidays", "leisure", "hotels", "gas", "petroleum", "oil", "oils",
        "svp", "vp", "avp", "evp", "ceo", "md", "cfo", "coo", "cto", "cio", "ed", "gm", "agm", 
        "dgm", "hr", "pr", "director", "president", "chairman", "secretary", "head", "lead", 
        "officer", "advisor", "consultant", "vice", "senior", "chief", "executive", "managing", "oil"
    )

    // Excluded uppercase words that often appear in articles or screenshots but are not stocks
    val EXCLUDED_UPPERCASE_WORDS = setOf(
        "NSE", "BSE", "INR", "USD", "IST", "GMT", "AM", "PM", "CEO", "MD", "FY", 
        "Q1", "Q2", "Q3", "Q4", "IPO", "CMP", "EPS", "PE", "LTP", "BUY", "SELL", 
        "HOLD", "NIFTY", "SENSEX", "BANKNIFTY", "FINNIFTY", "MIDCPNIFTY", "BANKEX", "INDIAVIX",
        "AND", "FOR", "THE", "WITH", "FROM", "THAT", "THIS", "WILL", "BE", "AN", "OR", "BY",
        "OK", "YES", "NO", "UP", "DOWN", "HIGH", "LOW", "OPEN", "CLOSE", "VOLUME", "VALUE",
        "WATCHLIST", "PORTFOLIO", "ORDERS", "ACCOUNT", "PROFILE", "DISCOVER", "NEWS", "HOME",
        "SVP", "VP", "AVP", "EVP", "COO", "CFO", "CTO", "CIO", "ED", "GM", "AGM", "DGM", "HR", "PR"
    )

    val GENERIC_COMPANY_WORDS = COMMON_OR_GENERIC_WORDS

    // First words shared by multiple distinct companies. Standalone matching on these 
    // first words is disabled to prevent massive multi-match false positives.
    val SHARED_FIRST_WORDS: Set<String> by lazy {
        kotakStockList
            .mapNotNull { stock ->
                stock.companyName.split(' ', '-', '_', '.').firstOrNull()?.trim()?.lowercase()
            }
            .filter { it.length >= 4 && !GENERIC_COMPANY_WORDS.contains(it) }
            .groupBy { it }
            .filter { it.value.size > 1 }
            .keys
    }

    fun hasFinancialContext(text: String): Boolean {
        val lowercaseText = text.lowercase()
        return FINANCIAL_KEYWORDS.any { keyword -> 
            lowercaseText.contains(Regex("\\b${Regex.escape(keyword)}\\b"))
        }
    }

    fun hasLocalFinancialContext(text: String, word: String): Boolean {
        val lowercaseText = text.lowercase()
        val lowercaseWord = word.lowercase()
        var index = lowercaseText.indexOf(lowercaseWord)
        while (index != -1) {
            val start = maxOf(0, index - 60)
            val end = minOf(lowercaseText.length, index + lowercaseWord.length + 60)
            val contextSnippet = lowercaseText.substring(start, end)
            
            val hasKeyword = FINANCIAL_KEYWORDS.any { keyword ->
                contextSnippet.contains(Regex("\\b${Regex.escape(keyword)}\\b"))
            }
            val hasNumericPattern = contextSnippet.contains(Regex("\\b\\d+(?:\\.\\d+)?%?\\b")) || 
                                     contextSnippet.contains("rs") || 
                                     contextSnippet.contains("₹")
            
            if (hasKeyword || hasNumericPattern) {
                return true
            }
            index = lowercaseText.indexOf(lowercaseWord, index + 1)
        }
        return false
    }

    fun hasImmediateFinancialContext(text: String, wordIndex: Int, wordLength: Int): Boolean {
        val start = maxOf(0, wordIndex - 30)
        val end = minOf(text.length, wordIndex + wordLength + 30)
        val snippet = text.substring(start, end).lowercase()
        
        val localKeywords = setOf("share", "stock", "price", "cmp", "buy", "sell", "hold", "target", "nse", "bse", "rs", "₹", "%")
        return localKeywords.any { keyword -> snippet.contains(keyword) }
    }

    fun hasCommodityTradingContext(text: String, word: String): Boolean {
        val lowercaseText = text.lowercase()
        val lowercaseWord = word.lowercase()
        var index = lowercaseText.indexOf(lowercaseWord)
        while (index != -1) {
            val start = maxOf(0, index - 80)
            val end = minOf(lowercaseText.length, index + lowercaseWord.length + 80)
            val contextSnippet = lowercaseText.substring(start, end)
            
            val commodityKeywords = setOf("mcx", "futures", "commodity", "per candy", "bales", "expiry", "lot size", "delivery", "trading at", "commodity market")
            val hasKeyword = commodityKeywords.any { keyword -> contextSnippet.contains(keyword) }
            
            if (hasKeyword) {
                return true
            }
            index = lowercaseText.indexOf(lowercaseWord, index + 1)
        }
        return false
    }

    fun truncateUnwantedSections(text: String): String {
        val lines = text.split('\n')
        val cleanLines = mutableListOf<String>()
        
        val endIndicators = listOf(
            "also read", "recommended for you", "you may like", "related news", "related stories",
            "related articles", "more from", "sponsored link", "promoted content", "advertisement",
            "trending news", "trending now", "editor's pick", "editors pick", "write a comment",
            "post a comment", "comment on this", "subscribe to", "newsletter", "follow us on",
            "disclaimer", "disclosure", "disclaimers", "disclosures", "terms of use", "all rights reserved"
        )
        
        for (line in lines) {
            val trimmedLine = line.trim()
            val lowerLine = trimmedLine.lowercase()
            
            val shouldTruncate = endIndicators.any { indicator ->
                lowerLine == indicator || 
                lowerLine.startsWith("$indicator:") || 
                lowerLine.startsWith("$indicator ") ||
                lowerLine.startsWith("legal $indicator") ||
                lowerLine.startsWith("important $indicator") ||
                lowerLine.contains("disclaimer:") ||
                lowerLine.contains("disclosure:") ||
                (lowerLine.length < 50 && (lowerLine.contains("recommended") || lowerLine.contains("related") || lowerLine.contains("sponsored") || lowerLine.contains("advertisement")))
            }
            
            if (shouldTruncate) {
                break
            }
            cleanLines.add(line)
        }
        
        return cleanLines.joinToString("\n")
    }

    fun isDisclaimerOrDisclosure(text: String): Boolean {
        val lower = text.lowercase().trim()
        val keywords = listOf(
            "disclaimer",
            "disclosure",
            "sebi registration",
            "sebi registered",
            "investment advisor",
            "investment adviser",
            "research analyst",
            "terms of use",
            "terms and conditions",
            "terms & conditions",
            "all rights reserved",
            "copyright",
            "written permission",
            "views and investment tips",
            "expressed by",
            "not represent the views",
            "advise users to check",
            "taking any investment decisions",
            "before taking any investment",
            "hold shares in the",
            "financial interest",
            "beneficial ownership",
            "conflict of interest",
            "subject company"
        )
        return keywords.any { lower.contains(it) }
    }

    fun extractCapitalizedPhrases(text: String): List<String> {
        val phrases = mutableListOf<String>()
        val regex = Regex("\\b[A-Z][a-zA-Z0-9&_]*(?:\\s+(?:of|and|in|the|for|&)\\s+[A-Z][a-zA-Z0-9&_]*|\\s+[A-Z][a-zA-Z0-9&_]*)*\\b")
        val matches = regex.findAll(text)
        for (match in matches) {
            val phrase = match.value.trim()
            if (phrase.length >= 2) {
                phrases.add(phrase)
            }
        }
        return phrases
    }

    val SINGLE_WORD_EXPLICIT_MAP = mapOf(
        "lic" to "LICI",
        "sbi" to "SBIN",
        "reliance" to "RELIANCE",
        "airtel" to "BHARTIARTL",
        "hdfc" to "HDFCBANK",
        "icici" to "ICICIBANK",
        "axis" to "AXISBANK",
        "kotak" to "KOTAKBANK",
        "wipro" to "WIPRO",
        "infosys" to "INFY",
        "tcs" to "TCS",
        "itc" to "ITC"
    )

    val AMBIGUOUS_SINGLE_WORD_FIRST_WORDS = setOf(
        "tata", "bajaj", "adani", "birla", "mahindra", "godrej", "larsen", "jsw", "gmr", "hinduja", "chola", "shriram"
    )

    data class PreComputedStock(
        val stock: KotakStockInfo,
        val symbolLower: String,
        val companyNameLower: String,
        val friendlyNameLower: String,
        val companyFirstWord: String?,
        val friendlyFirstWord: String?
    )

    private val preComputedStocks: List<PreComputedStock> by lazy {
        kotakStockList.map { stock ->
            val symbolLower = stock.symbol.lowercase()
            val companyNameLower = stock.companyName.lowercase()
            val friendlyName = stock.companyName
                .replace("Limited", "", ignoreCase = true)
                .replace("Ltd.", "", ignoreCase = true)
                .replace("Ltd", "", ignoreCase = true)
                .trim()
                .lowercase()
            val companyFirstWord = companyNameLower.split(' ', '-', '_', '.').firstOrNull()?.trim()
            val friendlyFirstWord = friendlyName.split(' ', '-', '_', '.').firstOrNull()?.trim()
            PreComputedStock(
                stock = stock,
                symbolLower = symbolLower,
                companyNameLower = companyNameLower,
                friendlyNameLower = friendlyName,
                companyFirstWord = companyFirstWord,
                friendlyFirstWord = friendlyFirstWord
            )
        }
    }

    private val symbolToStockMap: Map<String, PreComputedStock> by lazy {
        preComputedStocks.associateBy { it.symbolLower }
    }

    private val companyNameToStockMap: Map<String, PreComputedStock> by lazy {
        preComputedStocks.associateBy { it.companyNameLower }
    }

    private val friendlyNameToStockMap: Map<String, PreComputedStock> by lazy {
        preComputedStocks.associateBy { it.friendlyNameLower }
    }

    private val firstWordCandidateMap: Map<String, List<PreComputedStock>> by lazy {
        val map = mutableMapOf<String, MutableList<PreComputedStock>>()
        for (pcs in preComputedStocks) {
            val words = mutableSetOf<String>()
            pcs.companyFirstWord?.let { words.add(it) }
            pcs.friendlyFirstWord?.let { words.add(it) }
            for (w in words) {
                map.getOrPut(w) { mutableListOf() }.add(pcs)
            }
        }
        map
    }

    fun cleanStockPhrase(phrase: String): String {
        var clean = phrase.lowercase().trim()
        val suffixes = listOf(
            " limited", " ltd.", " ltd", " shares", " share", " stocks", " stock",
            " co.", " co", " company", " corp.", " corp", " corporation"
        )
        var modified = true
        while (modified) {
            modified = false
            for (suffix in suffixes) {
                if (clean.endsWith(suffix)) {
                    clean = clean.substring(0, clean.length - suffix.length).trim()
                    modified = true
                    break
                }
            }
        }
        return clean
    }

    fun preWarm() {
        val size1 = preComputedStocks.size
        val size2 = symbolToStockMap.size
        val size3 = companyNameToStockMap.size
        val size4 = friendlyNameToStockMap.size
        val size5 = firstWordCandidateMap.size
    }

    fun findSymbolForPhraseStrict(phrase: String, sentence: String): String? {
        val lowerPhrase = cleanStockPhrase(phrase)
        if (lowerPhrase.isEmpty()) return null
        val words = lowerPhrase.split(Regex("\\s+"))
        if (words.isEmpty()) return null
        
        val firstWord = words.first()
        
        // 0. Fast-path O(1) for single-word explicit overrides (e.g. Airtel, SBI, LIC)
        if (words.size == 1) {
            val mappedSymbol = SINGLE_WORD_EXPLICIT_MAP[firstWord]
            if (mappedSymbol != null) {
                val pcs = symbolToStockMap[mappedSymbol.lowercase()]
                if (pcs != null) {
                    val symbol = pcs.stock.symbol
                    // Check financial context
                    val stockKeywords = setOf(
                        "share", "shares", "stock", "stocks", "price", "prices", "cmp", "buy", "sell", 
                        "hold", "target", "nse", "bse", "results", "earnings", "dividend", "yield", 
                        "profit", "loss", "revenue", "quarter", "q1", "q2", "q3", "q4", "crore", "cr", "rs", "₹", "%"
                    )
                    val sentenceLower = sentence.lowercase()
                    val hasFinancialContext = stockKeywords.any { sentenceLower.contains(it) }
                    if (hasFinancialContext) {
                        return symbol
                    }
                }
                return null // Absolutely skip matching other stocks for this acronym/alias
            }
        }
        
        // 1. Match with exact symbol
        val stockBySymbol = symbolToStockMap[lowerPhrase]
        if (stockBySymbol != null) {
            val symbol = stockBySymbol.stock.symbol
            if (AMBIGUOUS_WORDS.contains(symbol)) {
                if (hasStrictLocalStockMarkers(phrase, 0, listOf(phrase), sentence)) {
                    return symbol
                }
            } else {
                return symbol
            }
        }
        
        // 2. Exact match with company name
        val stockByCompany = companyNameToStockMap[lowerPhrase]
        if (stockByCompany != null) {
            return stockByCompany.stock.symbol
        }
        
        // 3. Exact match with friendly name
        val stockByFriendly = friendlyNameToStockMap[lowerPhrase]
        if (stockByFriendly != null) {
            return stockByFriendly.stock.symbol
        }
        
        // Now, candidate lookup by first word
        val candidates = firstWordCandidateMap[firstWord] ?: emptyList()
        
        for (pcs in candidates) {
            val symbol = pcs.stock.symbol
            val companyNameLower = pcs.companyNameLower
            val friendlyName = pcs.friendlyNameLower
            
            // 4. Multi-word match representing the start of company name
            if (words.size >= 2) {
                if (companyNameLower.startsWith(lowerPhrase) || friendlyName.startsWith(lowerPhrase)) {
                    val isGenericPhrase = words.all { COMMON_OR_GENERIC_WORDS.contains(it) }
                    if (isGenericPhrase) {
                        val pattern = Regex("\\b${Regex.escape(phrase)}\\s+(?:Limited|Ltd|Corp|Corporation|Holdings|Group|Industries)\\b", RegexOption.IGNORE_CASE)
                        val containsMarkerInSentence = pattern.containsMatchIn(sentence) || 
                                                       sentence.lowercase().contains("share") || 
                                                       sentence.lowercase().contains("stock") ||
                                                       sentence.lowercase().contains("nse") ||
                                                       sentence.lowercase().contains("bse")
                        if (containsMarkerInSentence) {
                            return symbol
                        }
                    } else {
                        return symbol
                    }
                }
            }
            
            // 5. Single-word match
            if (words.size == 1) {
                // If it is a known ambiguous group-only word, do not match any standalone stock
                if (AMBIGUOUS_SINGLE_WORD_FIRST_WORDS.contains(firstWord)) {
                    continue
                }
                
                val stockFirstWord = pcs.companyFirstWord
                if (stockFirstWord == firstWord) {
                    if (COMMON_OR_GENERIC_WORDS.contains(firstWord)) {
                        val pattern = Regex("\\b${Regex.escape(phrase)}\\s+(?:Limited|Ltd|Corp|Corporation|Holdings|Group|Industries)\\b", RegexOption.IGNORE_CASE)
                        if (pattern.containsMatchIn(sentence)) {
                            return symbol
                        }
                    } else {
                        val stockKeywords = setOf(
                            "share", "shares", "stock", "stocks", "price", "prices", "cmp", "buy", "sell", 
                            "hold", "target", "nse", "bse", "results", "earnings", "dividend", "yield", 
                            "profit", "loss", "revenue", "quarter", "q1", "q2", "q3", "q4", "crore", "cr", "rs", "₹", "%"
                        )
                        val sentenceLower = sentence.lowercase()
                        val hasFinancialContext = stockKeywords.any { sentenceLower.contains(it) }
                        if (hasFinancialContext) {
                            return symbol
                        }
                    }
                }
            }
        }
        return null
    }

    fun isHighProbabilityStockInSentence(word: String, wordIndex: Int, words: List<String>, sentence: String): Boolean {
        val upperWord = word.uppercase()
        val isOriginalUppercase = word.all { it.isUpperCase() || it.isDigit() || it == '&' || it == '_' || it == '-' }
        
        if (AMBIGUOUS_WORDS.contains(upperWord)) {
            return hasStrictLocalStockMarkers(word, wordIndex, words, sentence)
        }
        
        if (isOriginalUppercase) {
            return true
        }
        
        val isTitleCase = word.length >= 2 && word.first().isUpperCase() && word.drop(1).all { it.isLowerCase() }
        if (isTitleCase) {
            val startIdx = maxOf(0, wordIndex - 4)
            val endIdx = minOf(words.size - 1, wordIndex + 4)
            val stockKeywords = setOf(
                "share", "shares", "stock", "stocks", "price", "prices", "cmp", "buy", "sell", 
                "hold", "target", "nse", "bse", "results", "earnings", "dividend", "yield", 
                "profit", "loss", "revenue", "quarter", "q1", "q2", "q3", "q4", "crore", "cr", "rs", "₹", "%"
            )
            for (j in startIdx..endIdx) {
                if (j == wordIndex) continue
                val surroundingWord = words[j].lowercase().replace(Regex("[^a-z0-9]"), "")
                if (stockKeywords.contains(surroundingWord)) {
                    return true
                }
            }
        }
        
        return false
    }

    fun hasStrictLocalStockMarkers(word: String, wordIndex: Int, words: List<String>, sentence: String): Boolean {
        val upperWord = word.uppercase()
        
        val shareOfPattern = Regex("\\b(?:shares|stock|buy|sell|hold|target|cmp|nse|bse|prices?|rs|₹)\\s+(?:of\\s+)?${Regex.escape(word)}\\b", RegexOption.IGNORE_CASE)
        val wordSharePattern = Regex("\\b${Regex.escape(word)}\\s+(?:shares?|stocks?|prices?|ltd|limited|corp|corporation|holdings|group|industries|securities|capital|finance)\\b", RegexOption.IGNORE_CASE)
        
        if (shareOfPattern.containsMatchIn(sentence) || wordSharePattern.containsMatchIn(sentence)) {
            return true
        }
        
        if (sentence.contains("($word)") || sentence.contains("[$word]") || sentence.contains("($upperWord)") || sentence.contains("[$upperWord]")) {
            return true
        }
        
        val nsePattern = Regex("\\b(?:nse|bse|ticker|symbol|scrip)\\s*:\\s*${Regex.escape(word)}\\b", RegexOption.IGNORE_CASE)
        if (nsePattern.containsMatchIn(sentence)) {
            return true
        }
        
        return false
    }

    /**
     * Checks if a word corresponds to any known base symbol or is a valid derivative contract.
     */
    fun matchesAnyScrip(word: String): Boolean {
        val upper = word.uppercase().trim()
        if (ALL_INDIAN_MARKET_SCRIPS.contains(upper)) {
            return true
        }
        
        // Exact matching aliases
        if (upper == "SBI" || upper == "LIC" || upper == "M_M" || upper == "BAJAJAUTO") {
            return true
        }
        
        // Match option and future contract formats
        val matchingBase = ALL_INDIAN_MARKET_SCRIPS.firstOrNull { base ->
            upper.startsWith(base) && upper.length > base.length
        }
        if (matchingBase != null && isValidContract(upper, matchingBase)) {
            return true
        }
        
        return false
    }

    /**
     * Identifies category of scrip.
     */
    fun getScripCategory(scrip: String): String {
        val upper = scrip.uppercase().trim()
        
        if (DERIVATIVE_INDICES.contains(upper)) {
            return "Derivative"
        }
        if (COMMODITIES.contains(upper)) {
            return "Commodity"
        }
        
        // Check if the scrip matches a derivative contract format of a base symbol
        val matchingBase = ALL_INDIAN_MARKET_SCRIPS.firstOrNull { base ->
            upper.startsWith(base) && upper.length > base.length
        }
        if (matchingBase != null && isValidContract(upper, matchingBase)) {
            return "Derivative"
        }
        
        return "Equity"
    }

    /**
     * Find stock info by symbol
     */
    fun findStockBySymbol(symbol: String): KotakStockInfo? {
        val upper = symbol.uppercase().trim()
        val matches = kotakStockList.filter { it.symbol == upper }
        return matches.firstOrNull { it.exchange.uppercase() == "NSE" } ?: matches.firstOrNull()
    }

    /**
     * Validates if a word is a valid option/futures contract for a given base symbol,
     * preventing over-matching (such as matching "LTCG" as a derivative of "LT").
     */
    fun isValidContract(word: String, base: String): Boolean {
        if (!word.startsWith(base)) return false
        val suffix = word.substring(base.length)
        if (suffix.isEmpty()) return false
        
        // Specific exact contract suffix matches
        if (suffix == "FUT" || suffix == "FUTR" || suffix == "CE" || suffix == "PE") {
            return true
        }
        
        // Option contracts ending in CE or PE
        if (suffix.endsWith("CE", ignoreCase = true) || suffix.endsWith("PE", ignoreCase = true)) {
            val inner = suffix.substring(0, suffix.length - 2)
            val containsDigits = inner.any { it.isDigit() }
            val containsMonth = containsMonthCode(inner)
            if (containsDigits || containsMonth || inner.isEmpty()) {
                return true
            }
        }
        
        // Futures contracts ending in FUT or FUTR
        if (suffix.endsWith("FUT", ignoreCase = true) || suffix.endsWith("FUTR", ignoreCase = true)) {
            val inner = if (suffix.endsWith("FUTR", ignoreCase = true)) {
                suffix.substring(0, suffix.length - 4)
            } else {
                suffix.substring(0, suffix.length - 3)
            }
            val containsDigits = inner.any { it.isDigit() }
            val containsMonth = containsMonthCode(inner)
            if (containsDigits || containsMonth || inner.isEmpty()) {
                return true
            }
        }
        
        // Monthly contract representation like "24DEC" or "24OCTFUT"
        if (suffix.length >= 5) {
            val containsDigits = suffix.any { it.isDigit() }
            val containsMonth = containsMonthCode(suffix)
            if (containsDigits && containsMonth) {
                return true
            }
        }
        
        return false
    }

    private fun containsMonthCode(text: String): Boolean {
        val upper = text.uppercase()
        val months = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
        return months.any { upper.contains(it) }
    }

    /**
     * Checks if the text contains a given word or phrase respecting word boundaries.
     */
    fun containsWordBoundaries(text: String, word: String): Boolean {
        val escaped = Regex.escape(word)
        val pattern = if (word.first().isLetterOrDigit() && word.last().isLetterOrDigit()) {
            "\\b$escaped\\b"
        } else {
            "(?:^|\\s|\\p{Punct})$escaped(?:$|\\s|\\p{Punct})"
        }
        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
        return regex.containsMatchIn(text)
    }

    /**
     * Extracts the first URL (http/https) from a given text string.
     */
    fun extractUrl(text: String): String? {
        val regex = Regex("https?://[^\\s]+")
        return regex.find(text)?.value
    }

    /**
     * Strictly validates if the URL originates from any supported source:
     * Moneycontrol, The Mint (livemint.com), Economic Times (economictimes.com), Business Line (thehindubusinessline.com)
     */
    fun isValidSourceUrl(urlString: String): Boolean {
        return try {
            val url = URL(urlString)
            val host = url.host.lowercase()
            host == "moneycontrol.com" || host.endsWith(".moneycontrol.com") ||
                    host == "livemint.com" || host.endsWith(".livemint.com") ||
                    host == "economictimes.com" || host.endsWith(".economictimes.com") ||
                    host == "indiatimes.com" || host.endsWith(".indiatimes.com") ||
                    host == "thehindubusinessline.com" || host.endsWith(".thehindubusinessline.com")
        } catch (e: Exception) {
            false
        }
    }

    fun isValidMoneycontrolUrl(urlString: String): Boolean {
        return isValidSourceUrl(urlString)
    }

    /**
     * Automatically suggests an elegant, relevant watchlist name (within 15 characters)
     * based on either the shared article url keywords or the current month/date.
     */
    fun suggestWatchlistName(url: String? = null): String {
        if (!url.isNullOrEmpty()) {
            try {
                val uri = java.net.URI(url)
                val path = uri.path ?: ""
                val segments = path.split('/', '-', '_', '.')
                    .map { it.lowercase().trim() }
                    .filter {
                        it.isNotEmpty() &&
                        it != "news" &&
                        it != "business" &&
                        it != "stocks" &&
                        it != "market" &&
                        it != "markets" &&
                        it != "articleshow" &&
                        it != "today" &&
                        it != "live" &&
                        it != "updates" &&
                        it != "share" &&
                        it != "price" &&
                        it != "html" &&
                        it != "htm" &&
                        it != "cms" &&
                        it != "amp" &&
                        !it.all { char -> char.isDigit() } &&
                        it.length >= 3
                    }
                if (segments.isNotEmpty()) {
                    val word1 = segments[0].replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString() }
                    val word2 = if (segments.size > 1) segments[1].replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString() } else ""
                    val candidate = if (word2.isNotEmpty() && (word1.length + word2.length + 1) <= 15) {
                        "$word1 $word2"
                    } else {
                        word1
                    }
                    if (candidate.length in 3..15) {
                        return candidate
                    } else if (candidate.length > 15) {
                        return candidate.take(15).trim()
                    }
                }
            } catch (e: Exception) {
                // fall through
            }
        }
        val sdf = java.text.SimpleDateFormat("MMM dd", java.util.Locale.US)
        val formattedDate = sdf.format(java.util.Calendar.getInstance().time)
        return "$formattedDate Picks"
    }

    /**
     * Extracts valid Indian market scrips (equities, derivatives, commodities)
     * using both precise NLP/fuzzy alias matching and bounded uppercase word extraction.
     */
    fun extractScripsFromText(text: String): List<String> {
        val matchedTickers = mutableSetOf<String>()
        
        // 1. Truncate unwanted sections (footer, sidebar, ads, recommendations)
        val cleanText = truncateUnwantedSections(text)
        
        // 2. Split the clean text into sentences by standard sentence punctuation or line breaks
        val sentences = cleanText.split(Regex("(?<=[.!?])\\s+|\\n+"))
        
        for (sentence in sentences) {
            val trimmedSentence = sentence.trim()
            if (trimmedSentence.isEmpty() || isDisclaimerOrDisclosure(trimmedSentence)) continue
            
            // Step 1: Scan proper noun phrases in this sentence and look them up strictly
            val capitalizedPhrases = extractCapitalizedPhrases(trimmedSentence)
            for (phrase in capitalizedPhrases) {
                val symbol = findSymbolForPhraseStrict(phrase, trimmedSentence)
                if (symbol != null) {
                    matchedTickers.add(symbol)
                }
            }
            
            // Step 2: Scan word-by-word for exact symbols/tickers with contextual validation
            val wordsInSentence = trimmedSentence.split(Regex("[\\s,;()\"']+"))
            for (i in wordsInSentence.indices) {
                val rawWord = wordsInSentence[i].trim()
                val word = rawWord.replace(Regex("^[^a-zA-Z0-9-&_]+|[^a-zA-Z0-9-&_]+$"), "")
                if (word.length < 2 || word.length > 20) continue
                
                val upperWord = word.uppercase()
                
                // Exclude common non-stock uppercase abbreviations
                if (EXCLUDED_UPPERCASE_WORDS.contains(upperWord)) {
                    continue
                }
                
                // Map common alias to standard ticker symbol
                val canonical = when (upperWord) {
                    "SBI" -> "SBIN"
                    "LIC" -> "LICI"
                    "M_M" -> "M&M"
                    "RAMCO" -> "RAMCOCEM"
                    else -> upperWord
                }
                
                // Check if it matches a known scrip base or derivative in the master list
                val isKnownSymbol = ALL_INDIAN_MARKET_SCRIPS.contains(canonical)
                
                if (isKnownSymbol) {
                    // Check commodities strictly using their commodity trading context
                    if (COMMODITIES.contains(canonical)) {
                        if (hasCommodityTradingContext(trimmedSentence, word)) {
                            matchedTickers.add(canonical)
                        }
                        continue
                    }
                    
                    // Verify if the word represents a high-probability stock in this sentence
                    if (isHighProbabilityStockInSentence(word, i, wordsInSentence, trimmedSentence)) {
                        matchedTickers.add(canonical)
                    }
                } else {
                    // Check if it is a valid option/futures contract
                    val matchingBase = ALL_INDIAN_MARKET_SCRIPS.firstOrNull { base ->
                        canonical.startsWith(base) && canonical.length > base.length
                    }
                    if (matchingBase != null && isValidContract(canonical, matchingBase)) {
                        if (isHighProbabilityStockInSentence(word, i, wordsInSentence, trimmedSentence)) {
                            matchedTickers.add(canonical)
                        }
                    }
                }
            }
        }
        
        // Filter out pure indices (e.g., NIFTY, SENSEX)
        val filteredTickers = matchedTickers.filter { ticker ->
            ticker !in DERIVATIVE_INDICES
        }
        
        return filteredTickers.toList().sorted()
    }

    /**
     * Connects to the URL via Jsoup, downloads the body text, targets the main content block,
     * and extracts all valid Indian market scrips.
     */
    suspend fun extractScripsFromUrl(urlString: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.connect(urlString)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .timeout(3000)
                .get()

            // 1. Extract Title
            var titleText = doc.select("h1.article_title").text()
            if (titleText.isEmpty()) {
                titleText = doc.select("h1").firstOrNull()?.text() ?: ""
            }

            // 2. Extract and Clean Main Content Block
            var bodyText = ""
            // Prioritize more specific containers (like .arti-flow, #article-body) first
            val bodySelector = "div.arti-flow, div.news_post, #article-body, article, div.content_wrapper, div.content, #content, div.paywall, div.storyPage, div.artText, div.story-text"
            val selectedElement = doc.select(bodySelector).firstOrNull()
            
            if (selectedElement != null) {
                val cleanElement = selectedElement.clone()
                // Extensively and recursively strip sidebars, tickers, widgets, and tables containing unrelated links/stats
                val selectorsToRemove = listOf(
                    "aside", "header", "footer", "nav", "table", "iframe", "noscript", "script", "style",
                    "div.header", "div.footer", "div.nav", "div.menu", "div.top-bar", "div.navigation",
                    "[class*=sidebar]", "[id*=sidebar]",
                    "[class*=right-col]", "[class*=right_col]", "[id*=right-col]", "[id*=right_col]",
                    "[class*=trending]", "[id*=trending]",
                    "[class*=ticker]", "[id*=ticker]",
                    "[class*=widget]", "[id*=widget]",
                    "[class*=promo]", "[id*=promo]",
                    "[class*=related]", "[id*=related]",
                    "[class*=social]", "[id*=social]",
                    "[class*=share]", "[id*=share]",
                    "[class*=newsletter]", "[class*=subscribe]",
                    "[class*=ad-]", "[class*=ad_]", "[id*=ad-]", "[id*=ad_]", "[class*=advertisement]",
                    "[class*=most-active]", "[class*=market-map]", "[class*=market_]",
                    "div.tags-list", "div.author-info", "div.comment-box", "div.comments"
                )
                for (selector in selectorsToRemove) {
                    cleanElement.select(selector).remove()
                }
                bodyText = cleanElement.text()
            }
            if (bodyText.isEmpty()) {
                val cleanBody = doc.body()?.clone()
                if (cleanBody != null) {
                    val selectorsToRemove = listOf(
                        "aside", "header", "footer", "nav", "table", "iframe", "noscript", "script", "style",
                        "div.header", "div.footer", "div.nav", "div.menu", "div.top-bar", "div.navigation",
                        "[class*=sidebar]", "[id*=sidebar]",
                        "[class*=right-col]", "[class*=right_col]", "[id*=right-col]", "[id*=right_col]",
                        "[class*=trending]", "[id*=trending]",
                        "[class*=ticker]", "[id*=ticker]",
                        "[class*=widget]", "[id*=widget]",
                        "[class*=promo]", "[id*=promo]",
                        "[class*=related]", "[id*=related]",
                        "[class*=social]", "[id*=social]",
                        "[class*=share]", "[id*=share]",
                        "[class*=newsletter]", "[class*=subscribe]",
                        "[class*=ad-]", "[class*=ad_]", "[id*=ad-]", "[id*=ad_]", "[class*=advertisement]",
                        "[class*=most-active]", "[class*=market-map]", "[class*=market_]",
                        "div.tags-list", "div.author-info", "div.comment-box", "div.comments"
                    )
                    for (selector in selectorsToRemove) {
                        cleanBody.select(selector).remove()
                    }
                    bodyText = cleanBody.text()
                } else {
                    bodyText = doc.body()?.text() ?: ""
                }
            }

            // Combine Title and clean Body Text
            val fullTextToScan = if (titleText.isNotEmpty()) {
                "$titleText\n\n$bodyText"
            } else {
                bodyText
            }

            extractScripsFromText(fullTextToScan)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
