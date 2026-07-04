package com.example.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URL

object ScripExtractor {

    data class StockInfo(
        val ticker: String,
        val fullName: String,
        val aliases: List<String>
    )

    // Top 20 NSE Stocks validation list to avoid false positives (standard ticker names)
    val TOP_20_NSE_STOCKS = setOf(
        "RELIANCE", 
        "TCS", 
        "HDFCBANK", 
        "BHARTIARTL", 
        "ICICIBANK", 
        "INFY", 
        "SBIN", 
        "SBI", // Commonly written
        "LICI", 
        "LIC", // Commonly written
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

    // Comprehensive list of Nifty 500 stocks with full names and friendly/short name aliases
    val NIFTY_500_STOCKS = listOf(
        StockInfo("RELIANCE", "Reliance Industries Limited", listOf("Reliance Industries", "Reliance", "RIL")),
        StockInfo("TCS", "Tata Consultancy Services Limited", listOf("Tata Consultancy Services", "TCS", "Tata Consultancy")),
        StockInfo("HDFCBANK", "HDFC Bank Limited", listOf("HDFC Bank", "HDFC")),
        StockInfo("BHARTIARTL", "Bharti Airtel Limited", listOf("Bharti Airtel", "Airtel")),
        StockInfo("ICICIBANK", "ICICI Bank Limited", listOf("ICICI Bank", "ICICI")),
        StockInfo("INFY", "Infosys Limited", listOf("Infosys")),
        StockInfo("SBIN", "State Bank of India", listOf("State Bank of India", "SBI", "State Bank")),
        StockInfo("LICI", "Life Insurance Corporation of India", listOf("Life Insurance Corporation", "LIC")),
        StockInfo("ITC", "ITC Limited", listOf("ITC")),
        StockInfo("HINDUNILVR", "Hindustan Unilever Limited", listOf("Hindustan Unilever", "HUL")),
        StockInfo("LT", "Larsen & Toubro Limited", listOf("Larsen & Toubro", "L&T", "Larsen", "Toubro")),
        StockInfo("HCLTECH", "HCL Technologies Limited", listOf("HCL Technologies", "HCL")),
        StockInfo("BAJFINANCE", "Bajaj Finance Limited", listOf("Bajaj Finance")),
        StockInfo("SUNPHARMA", "Sun Pharmaceutical Industries Limited", listOf("Sun Pharmaceutical", "Sun Pharma")),
        StockInfo("MARUTI", "Maruti Suzuki India Limited", listOf("Maruti Suzuki", "Maruti")),
        StockInfo("ADANIENT", "Adani Enterprises Limited", listOf("Adani Enterprises", "Adani")),
        StockInfo("NTPC", "NTPC Limited", listOf("NTPC")),
        StockInfo("TATAMOTORS", "Tata Motors Limited", listOf("Tata Motors")),
        StockInfo("ONGC", "Oil and Natural Gas Corporation Limited", listOf("Oil and Natural Gas", "ONGC")),
        StockInfo("AXISBANK", "Axis Bank Limited", listOf("Axis Bank", "Axis")),
        
        // Defence & Aeronautics
        StockInfo("BEL", "Bharat Electronics Limited", listOf("Bharat Electronics", "BEL")),
        StockInfo("HAL", "Hindustan Aeronautics Limited", listOf("Hindustan Aeronautics", "HAL")),
        StockInfo("BHEL", "Bharat Heavy Electricals Limited", listOf("Bharat Heavy Electricals", "BHEL")),
        StockInfo("BDL", "Bharat Dynamics Limited", listOf("Bharat Dynamics", "BDL")),
        StockInfo("MAZDOCK", "Mazagon Dock Shipbuilders Limited", listOf("Mazagon Dock Shipbuilders", "Mazdock", "Mazagon Dock")),
        StockInfo("PARAS", "Paras Defence and Space Technologies Limited", listOf("Paras Defence and Space", "Paras Defence", "Paras")),
        StockInfo("COCHINSHIP", "Cochin Shipyard Limited", listOf("Cochin Shipyard", "Cochin")),
        StockInfo("GRSE", "Garden Reach Shipbuilders and Engineers Limited", listOf("Garden Reach Shipbuilders", "GRSE")),
        
        // Financials & Others
        StockInfo("COALINDIA", "Coal India Limited", listOf("Coal India")),
        StockInfo("WIPRO", "Wipro Limited", listOf("Wipro")),
        StockInfo("KOTAKBANK", "Kotak Mahindra Bank Limited", listOf("Kotak Mahindra", "Kotak Bank", "Kotak")),
        StockInfo("ASIANPAINT", "Asian Paints Limited", listOf("Asian Paints", "Asian Paint")),
        StockInfo("ULTRACEMCO", "UltraTech Cement Limited", listOf("UltraTech Cement", "UltraTech")),
        StockInfo("TITAN", "Titan Company Limited", listOf("Titan")),
        StockInfo("DMART", "Avenue Supermarts Limited", listOf("Avenue Supermarts", "DMart")),
        StockInfo("BAJAJFINSV", "Bajaj Finserv Limited", listOf("Bajaj Finserv")),
        StockInfo("NESTLEIND", "Nestle India Limited", listOf("Nestle India", "Nestle")),
        StockInfo("ADANIPORTS", "Adani Ports and Special Economic Zone Limited", listOf("Adani Ports")),
        StockInfo("JSWSTEEL", "JSW Steel Limited", listOf("JSW Steel", "JSW")),
        StockInfo("GRASIM", "Grasim Industries Limited", listOf("Grasim")),
        StockInfo("POWERGRID", "Power Grid Corporation of India Limited", listOf("Power Grid", "POWERGRID")),
        StockInfo("TATASTEEL", "Tata Steel Limited", listOf("Tata Steel")),
        StockInfo("TECHM", "Tech Mahindra Limited", listOf("Tech Mahindra", "TechM")),
        StockInfo("HINDALCO", "Hindalco Industries Limited", listOf("Hindalco")),
        StockInfo("ADANIPOWER", "Adani Power Limited", listOf("Adani Power")),
        StockInfo("CIPLA", "Cipla Limited", listOf("Cipla")),
        StockInfo("M&M", "Mahindra and Mahindra Limited", listOf("Mahindra & Mahindra", "Mahindra", "M&M")),
        StockInfo("JIOFIN", "Jio Financial Services Limited", listOf("Jio Financial Services", "Jio Financial", "Jio Fin")),
        StockInfo("SBILIFE", "SBI Life Insurance Company Limited", listOf("SBI Life")),
        StockInfo("BPCL", "Bharat Petroleum Corporation Limited", listOf("Bharat Petroleum", "BPCL")),
        StockInfo("BAJAJAUTO", "Bajaj Auto Limited", listOf("Bajaj Auto")),
        StockInfo("INDUSINDBK", "IndusInd Bank Limited", listOf("IndusInd Bank")),
        StockInfo("EICHERMOT", "Eicher Motors Limited", listOf("Eicher Motors", "Eicher")),
        StockInfo("DRREDDY", "Dr. Reddy's Laboratories Limited", listOf("Dr. Reddy", "Dr Reddys")),
        StockInfo("BRITANNIA", "Britannia Industries Limited", listOf("Britannia")),
        StockInfo("DIVISLAB", "Divi's Laboratories Limited", listOf("Divis Labs", "Divis Laboratories")),
        StockInfo("TATACONSUM", "Tata Consumer Products Limited", listOf("Tata Consumer")),
        StockInfo("APOLLOHOSP", "Apollo Hospitals Enterprise Limited", listOf("Apollo Hospitals", "Apollo Hospital")),
        StockInfo("LTIM", "LTIMindtree Limited", listOf("LTIMindtree", "LTI", "Mindtree")),
        StockInfo("HEROMOTOCO", "Hero MotoCorp Limited", listOf("Hero MotoCorp", "Hero Honda", "Hero")),
        StockInfo("SHRIRAMFIN", "Shriram Finance Limited", listOf("Shriram Finance")),
        StockInfo("TRENT", "Trent Limited", listOf("Trent")),
        StockInfo("SIEMENS", "Siemens Limited", listOf("Siemens")),
        StockInfo("DLF", "DLF Limited", listOf("DLF")),
        StockInfo("ZOMATO", "Zomato Limited", listOf("Zomato")),
        StockInfo("PAYTM", "One97 Communications Limited", listOf("Paytm", "One97")),
        StockInfo("NYKAA", "FSN E-Commerce Ventures Limited", listOf("Nykaa")),
        StockInfo("PBFINTECH", "PB Fintech Limited", listOf("Policybazaar", "Policy Bazaar", "PB Fintech")),
        StockInfo("DELHIVERY", "Delhivery Limited", listOf("Delhivery")),
        StockInfo("TATAPOWER", "Tata Power Company Limited", listOf("Tata Power")),
        StockInfo("YESBANK", "Yes Bank Limited", listOf("Yes Bank")),
        StockInfo("PNB", "Punjab National Bank", listOf("Punjab National Bank", "PNB")),
        StockInfo("CANBK", "Canara Bank", listOf("Canara Bank", "Canara")),
        StockInfo("BOB", "Bank of Baroda", listOf("Bank of Baroda", "BOB")),
        StockInfo("UNIONBANK", "Union Bank of India", listOf("Union Bank")),
        StockInfo("BANKBARODA", "Bank of Baroda", listOf("Bank of Baroda", "BOB")),
        StockInfo("BANKINDIA", "Bank of India", listOf("Bank of India")),
        
        // Additional Nifty 500 stocks to cover a wide spectrum of companies
        StockInfo("SUZLON", "Suzlon Energy Limited", listOf("Suzlon Energy", "Suzlon")),
        StockInfo("IRFC", "Indian Railway Finance Corporation Limited", listOf("Indian Railway Finance Corporation", "IRFC")),
        StockInfo("RVNL", "Rail Vikas Nigam Limited", listOf("Rail Vikas Nigam", "RVNL")),
        StockInfo("IREDA", "Indian Renewable Energy Development Agency Limited", listOf("IREDA")),
        StockInfo("NHPC", "NHPC Limited", listOf("NHPC")),
        StockInfo("SJVN", "SJVN Limited", listOf("SJVN")),
        StockInfo("HUDCO", "Housing and Urban Development Corporation Limited", listOf("HUDCO")),
        StockInfo("PFC", "Power Finance Corporation Limited", listOf("Power Finance Corporation", "PFC")),
        StockInfo("REC", "REC Limited", listOf("REC")),
        
        StockInfo("ASHOKLEY", "Ashok Leyland Limited", listOf("Ashok Leyland")),
        StockInfo("TATACHEM", "Tata Chemicals Limited", listOf("Tata Chemicals", "Tata Chem")),
        StockInfo("CHAMBLFERT", "Chambal Fertilisers and Chemicals Limited", listOf("Chambal Fertilisers")),
        StockInfo("COROMANDEL", "Coromandel International Limited", listOf("Coromandel")),
        StockInfo("FACT", "Fertilisers and Chemicals Travancore Limited", listOf("FACT")),
        StockInfo("RCF", "Rashtriya Chemicals and Fertilizers Limited", listOf("Rashtriya Chemicals", "RCF")),
        StockInfo("NFL", "National Fertilizers Limited", listOf("National Fertilizers", "NFL")),
        
        StockInfo("DEEPAKNTR", "Deepak Nitrite Limited", listOf("Deepak Nitrite")),
        StockInfo("TATACOMM", "Tata Communications Limited", listOf("Tata Communications")),
        StockInfo("TATAELXSI", "Tata Elxsi Limited", listOf("Tata Elxsi")),
        StockInfo("KPITTECH", "KPIT Technologies Limited", listOf("KPIT Technologies", "KPIT")),
        StockInfo("COFORGE", "Coforge Limited", listOf("Coforge")),
        StockInfo("PERSISTENT", "Persistent Systems Limited", listOf("Persistent Systems", "Persistent")),
        StockInfo("OFSS", "Oracle Financial Services Software Limited", listOf("OFSS", "Oracle Financial")),
        StockInfo("MPHASIS", "Mphasis Limited", listOf("Mphasis")),
        
        StockInfo("BIOCON", "Biocon Limited", listOf("Biocon")),
        StockInfo("LUPIN", "Lupin Limited", listOf("LUPIN")),
        StockInfo("AUROPHARMA", "Aurobindo Pharma Limited", listOf("Aurobindo Pharma")),
        StockInfo("GLENMARK", "Glenmark Pharmaceuticals Limited", listOf("Glenmark")),
        StockInfo("IPCALAB", "Ipca Laboratories Limited", listOf("Ipca Labs")),
        StockInfo("LAURUSLABS", "Laurus Labs Limited", listOf("Laurus Labs")),
        
        StockInfo("KALYANKJIL", "Kalyan Jewellers India Limited", listOf("Kalyan Jewellers")),
        StockInfo("SENCO", "Senco Gold Limited", listOf("Senco Gold", "Senco")),
        StockInfo("PIDILITIND", "Pidilite Industries Limited", listOf("Pidilite", "Fevicol")),
        StockInfo("JUBLFOOD", "Jubilant Foodworks Limited", listOf("Jubilant Foodworks", "Domino's")),
        StockInfo("VBL", "Varun Beverages Limited", listOf("Varun Beverages", "PepsiCo India")),
        StockInfo("DABUR", "Dabur India Limited", listOf("Dabur")),
        StockInfo("MARICO", "Marico Limited", listOf("Marico")),
        StockInfo("COLPAL", "Colgate-Palmolive (India) Limited", listOf("Colgate")),
        
        StockInfo("SAIL", "Steel Authority of India Limited", listOf("Steel Authority of India", "SAIL")),
        StockInfo("VEDL", "Vedanta Limited", listOf("Vedanta")),
        StockInfo("HINDZINC", "Hindustan Zinc Limited", listOf("Hindustan Zinc")),
        StockInfo("TVSMOTOR", "TVS Motor Company Limited", listOf("TVS Motor")),
        StockInfo("ESCORTS", "Escorts Kubota Limited", listOf("Escorts Kubota", "Escorts")),
        StockInfo("EXIDEIND", "Exide Industries Limited", listOf("Exide")),
        StockInfo("BOSCH", "Bosch Limited", listOf("Bosch")),
        StockInfo("UNOMINDA", "Uno Minda Limited", listOf("Uno Minda")),
        StockInfo("BHARATFORG", "Bharat Forge Limited", listOf("Bharat Forge")),
        StockInfo("CGPOWER", "CG Power and Industrial Solutions Limited", listOf("CG Power")),
        StockInfo("CUMMINSIND", "Cummins India Limited", listOf("Cummins")),
        StockInfo("ABB", "ABB India Limited", listOf("ABB")),
        StockInfo("HONAUT", "Honeywell Automation India Limited", listOf("Honeywell")),
        StockInfo("THERMAX", "Thermax Limited", listOf("Thermax")),
        
        StockInfo("CONCOR", "Container Corporation of India Limited", listOf("CONCOR")),
        StockInfo("BLUEDART", "Blue Dart Express Limited", listOf("Blue Dart")),
        StockInfo("VOLTAS", "Voltas Limited", listOf("Voltas")),
        StockInfo("BLUESTARCO", "Blue Star Limited", listOf("Blue Star")),
        StockInfo("POLYCAB", "Polycab India Limited", listOf("Polycab")),
        StockInfo("ASTRAL", "Astral Limited", listOf("Astral Pipes", "Astral")),
        StockInfo("DIXON", "Dixon Technologies (India) Limited", listOf("Dixon Technologies", "Dixon")),
        StockInfo("KAYNES", "Kaynes Technology India Limited", listOf("Kaynes Technology", "Kaynes")),
        
        StockInfo("PRESTIGE", "Prestige Estates Projects Limited", listOf("Prestige")),
        StockInfo("SOBHA", "Sobha Limited", listOf("Sobha")),
        StockInfo("BRIGADE", "Brigade Enterprises Limited", listOf("Brigade")),
        StockInfo("MRF", "MRF Limited", listOf("MRF")),
        StockInfo("APOLLOTYRE", "Apollo Tyres Limited", listOf("Apollo Tyres", "Apollo Tyre")),
        StockInfo("CEATLTD", "CEAT Limited", listOf("CEAT")),
        StockInfo("BALKRISIND", "Balkrishna Industries Limited", listOf("Balkrishna Industries", "BKT")),
        StockInfo("MCX", "Multi Commodity Exchange of India Limited", listOf("MCX")),
        StockInfo("BSE", "BSE Limited", listOf("BSE")),
        StockInfo("CDSL", "Central Depository Services (India) Limited", listOf("CDSL")),
        StockInfo("ANGELONE", "Angel One Limited", listOf("Angel One")),
        StockInfo("IEX", "Indian Energy Exchange Limited", listOf("IEX")),
        StockInfo("APLAPOLLO", "APL Apollo Tubes Limited", listOf("APL Apollo")),
        StockInfo("NMDC", "NMDC Limited", listOf("NMDC")),
        StockInfo("HINDCOPPER", "Hindustan Copper Limited", listOf("Hindustan Copper")),
        StockInfo("NATIONALUM", "National Aluminium Company Limited", listOf("National Aluminium", "NALCO")),
        StockInfo("SHREECEM", "Shree Cement Limited", listOf("Shree Cement", "Bangur")),
        StockInfo("ACC", "ACC Limited", listOf("ACC")),
        StockInfo("AMBUJACEM", "Ambuja Cements Limited", listOf("Ambuja Cement", "Ambuja")),
        StockInfo("JKCEMENT", "JK Cement Limited", listOf("JK Cement")),
        StockInfo("RAMCO", "The Ramco Cements Limited", listOf("Ramco Cement", "Ramco")),
        StockInfo("PIIND", "PI Industries Limited", listOf("PI Industries")),
        StockInfo("UPL", "UPL Limited", listOf("UPL")),
        StockInfo("SRF", "SRF Limited", listOf("SRF")),
        StockInfo("PRAJIND", "Praj Industries Limited", listOf("Praj Industries", "Praj")),
        StockInfo("RITES", "RITES Limited", listOf("RITES")),
        StockInfo("IRCON", "Ircon International Limited", listOf("IRCON")),
        StockInfo("TITAGARH", "Titagarh Rail Systems Limited", listOf("Titagarh")),
        StockInfo("JWL", "Jupiter Wagons Limited", listOf("Jupiter Wagons", "Jupiter Wagon")),
        StockInfo("BEML", "BEML Limited", listOf("BEML")),
        
        StockInfo("ABFRL", "Aditya Birla Fashion and Retail Limited", listOf("ABFRL", "Aditya Birla Fashion")),
        StockInfo("RAYMOND", "Raymond Limited", listOf("Raymond")),
        StockInfo("WELSPUNLIV", "Welspun Living Limited", listOf("Welspun Living", "Welspun")),
        StockInfo("CENTURYPLY", "Century Plyboards (India) Limited", listOf("Century Ply")),
        StockInfo("EQUITAS", "Equitas Small Finance Bank Limited", listOf("Equitas")),
        StockInfo("UJJIVAN", "Ujjivan Small Finance Bank Limited", listOf("UJJIVAN")),
        StockInfo("AUBANK", "AU Small Finance Bank Limited", listOf("AU Small Finance", "AU Bank")),
        StockInfo("FEDERALBNK", "Federal Bank Limited", listOf("Federal Bank")),
        StockInfo("IDFCFIRSTB", "IDFC First Bank Limited", listOf("IDFC First")),
        StockInfo("BANDHANBNK", "Bandhan Bank Limited", listOf("Bandhan Bank")),
        StockInfo("RBLBANK", "RBL Bank Limited", listOf("RBL Bank")),
        
        StockInfo("GMRINFRA", "GMR Airports Infrastructure Limited", listOf("GMR Airports", "GMR Infrastructure", "GMR")),
        StockInfo("ADANIGREEN", "Adani Green Energy Limited", listOf("Adani Green")),
        StockInfo("AWL", "Adani Wilmar Limited", listOf("Adani Wilmar")),
        StockInfo("JSWENERGY", "JSW Energy Limited", listOf("JSW Energy")),
        StockInfo("TORNTPOWER", "Torrent Power Limited", listOf("Torrent Power")),
        StockInfo("CESC", "CESC Limited", listOf("CESC")),
        
        StockInfo("METROPOLIS", "Metropolis Healthcare Limited", listOf("Metropolis Healthcare", "Metropolis")),
        StockInfo("LALPATHLAB", "Dr. Lal PathLabs Limited", listOf("Lal PathLabs", "Dr Lal Path")),
        StockInfo("FORTIS", "Fortis Healthcare Limited", listOf("Fortis Healthcare", "Fortis")),
        StockInfo("MAXHEALTH", "Max Healthcare Institute Limited", listOf("Max Healthcare", "Max Health")),
        StockInfo("GLOBALHEALTH", "Global Health Limited", listOf("Medanta", "Global Health")),
        StockInfo("KIMS", "Krishna Institute of Medical Sciences Limited", listOf("KIMS")),
        StockInfo("NH", "Narayana Hrudayalaya Limited", listOf("Narayana Hrudayalaya", "Narayana Health")),
        
        StockInfo("ZEEL", "Zee Entertainment Enterprises Limited", listOf("ZEEL", "Zee Entertainment", "Zee TV")),
        StockInfo("PVRINOX", "PVR INOX Limited", listOf("PVR INOX", "PVR", "Inox")),
        StockInfo("SUNTV", "Sun TV Network Limited", listOf("Sun TV")),
        
        StockInfo("GODREJPROP", "Godrej Properties Limited", listOf("Godrej Properties", "Godrej")),
        StockInfo("LODHA", "Macrotech Developers Limited", listOf("Macrotech Developers", "Lodha")),
        StockInfo("OBERREALTY", "Oberoi Realty Limited", listOf("Oberoi Realty", "Oberoi")),
        
        StockInfo("INDIGO", "InterGlobe Aviation Limited", listOf("InterGlobe Aviation", "IndiGo")),
        StockInfo("ROUTE", "Route Mobile Limited", listOf("Route Mobile")),
        StockInfo("TANLA", "Tanla Platforms Limited", listOf("Tanla Platforms", "Tanla")),
        
        StockInfo("MUTHOOTFIN", "Muthoot Finance Limited", listOf("Muthoot Finance")),
        StockInfo("MANAPPURAM", "Manappuram Finance Limited", listOf("Manappuram Finance", "Manappuram")),
        StockInfo("CHOLAFIN", "Cholamandalam Investment and Finance Company Limited", listOf("Cholamandalam", "Chola")),
        
        StockInfo("ACI", "Alkyl Amines Chemicals Limited", listOf("Alkyl Amines")),
        StockInfo("BALAMINES", "Balaji Amines Limited", listOf("Balaji Amines")),
        StockInfo("GNFC", "Gujarat Narmada Valley Fertilizers and Chemicals Limited", listOf("GNFC")),
        StockInfo("GSFC", "Gujarat State Fertilizers and Chemicals Limited", listOf("GSFC")),
        
        StockInfo("NATCOPHARM", "Natco Pharma Limited", listOf("Natco Pharma")),
        StockInfo("ALKEM", "Alkem Laboratories Limited", listOf("Alkem Laboratories", "Alkem")),
        StockInfo("GLAND", "Gland Pharma Limited", listOf("Gland Pharma")),
        
        StockInfo("CARTRADE", "CarTrade Tech Limited", listOf("CarTrade")),
        StockInfo("NAZARA", "Nazara Technologies Limited", listOf("Nazara Technologies", "Nazara")),
        StockInfo("MAPMYINDIA", "C.E. Info Systems Limited", listOf("MapmyIndia", "CE Info Systems")),
        StockInfo("NAUKRI", "Info Edge (India) Limited", listOf("Info Edge", "Naukri", "Naukri.com")),
        
        StockInfo("STARHEALTH", "Star Health and Allied Insurance Company Limited", listOf("Star Health")),
        StockInfo("ICICIPRULI", "ICICI Prudential Life Insurance Company Limited", listOf("ICICI Prudential", "ICICI Pru")),
        StockInfo("ICICIGI", "ICICI Lombard General Insurance Company Limited", listOf("ICICI Lombard")),
        StockInfo("HDFCLIFE", "HDFC Life Insurance Company Limited", listOf("HDFC Life")),
        StockInfo("GICRE", "General Insurance Corporation of India", listOf("GIC Re", "GICRE")),
        StockInfo("NIACL", "The New India Assurance Company Limited", listOf("New India Assurance", "NIACL")),
        StockInfo("PCJEWELLER", "PC Jeweller Limited", listOf("PC Jeweller", "PCJEWELLER")),
        StockInfo("STLTECH", "Sterlite Technologies Limited", listOf("Sterlite Technologies", "Sterlite Tech", "STLTECH"))
    )

    // Complete set of all available scrip symbols for validation, searching, etc.
    val ALL_INDIAN_MARKET_SCRIPS: Set<String> = (
        DERIVATIVE_INDICES + 
        COMMODITIES + 
        NIFTY_500_STOCKS.map { it.ticker } +
        listOf("SBI", "LIC", "M_M", "BAJAJAUTO", "RAMCOCEM")
    ).toSet()

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
     * Strictly validates if the URL originates from moneycontrol.com
     */
    fun isValidMoneycontrolUrl(urlString: String): Boolean {
        return try {
            val url = URL(urlString)
            val host = url.host.lowercase()
            host == "moneycontrol.com" || host.endsWith(".moneycontrol.com")
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Extracts valid Indian market scrips (equities, derivatives, commodities)
     * using both precise NLP/fuzzy alias matching and bounded uppercase word extraction.
     */
    fun extractScripsFromText(text: String): List<String> {
        val matchedTickers = mutableSetOf<String>()
        val lowercaseText = text.lowercase()
        
        // 1. Fuzzy & friendly-name scanning (e.g. "Reliance" -> "RELIANCE", "Airtel" -> "BHARTIARTL")
        for (stock in NIFTY_500_STOCKS) {
            if (containsWordBoundaries(lowercaseText, stock.fullName)) {
                matchedTickers.add(stock.ticker)
                continue
            }
            for (alias in stock.aliases) {
                if (containsWordBoundaries(lowercaseText, alias)) {
                    matchedTickers.add(stock.ticker)
                    break
                }
            }
        }
        
        // 2. Extracted uppercase word matching for exact tickers and derivative contracts
        val regex = Regex("\\b[A-Z0-9-&_]{2,20}\\b")
        val matches = regex.findAll(text).map { it.value }.toSet()
        for (match in matches) {
            val upper = match.uppercase().trim()
            if (ALL_INDIAN_MARKET_SCRIPS.contains(upper)) {
                // Map common aliases directly to their canonical tickers
                val canonical = when (upper) {
                    "SBI" -> "SBIN"
                    "LIC" -> "LICI"
                    "M_M" -> "M&M"
                    "RAMCOCEM" -> "RAMCO"
                    else -> upper
                }
                matchedTickers.add(canonical)
            } else {
                // Option/futures contract matching of valid base symbols
                val matchingBase = ALL_INDIAN_MARKET_SCRIPS.firstOrNull { base ->
                    upper.startsWith(base) && upper.length > base.length
                }
                if (matchingBase != null && isValidContract(upper, matchingBase)) {
                    matchedTickers.add(upper)
                }
            }
        }
        
        return matchedTickers.toList().sorted()
    }

    /**
     * Connects to the URL via Jsoup, downloads the body text, targets the main content block,
     * and extracts all valid Indian market scrips.
     */
    suspend fun extractScripsFromUrl(urlString: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.connect(urlString)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .timeout(10000)
                .get()

            // 1. Extract Title
            var titleText = doc.select("h1.article_title").text()
            if (titleText.isEmpty()) {
                titleText = doc.select("h1").firstOrNull()?.text() ?: ""
            }

            // 2. Extract and Clean Main Content Block
            var bodyText = ""
            // Prioritize more specific containers (like .arti-flow, #article-body) first
            val bodySelector = "div.arti-flow, div.news_post, #article-body, article, div.content_wrapper, div.content, #content"
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
