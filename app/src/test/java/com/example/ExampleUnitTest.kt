package com.example

import com.example.utils.ScripExtractor
import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testScripMatching_Equities() {
    assertTrue(ScripExtractor.matchesAnyScrip("RELIANCE"))
    assertTrue(ScripExtractor.matchesAnyScrip("TCS"))
    assertTrue(ScripExtractor.matchesAnyScrip("ZOMATO"))
    assertTrue(ScripExtractor.matchesAnyScrip("PAYTM"))
    assertFalse(ScripExtractor.matchesAnyScrip("INVALIDTICKERNAME"))
  }

  @Test
  fun testScripMatching_Commodities() {
    assertTrue(ScripExtractor.matchesAnyScrip("GOLD"))
    assertTrue(ScripExtractor.matchesAnyScrip("SILVER"))
    assertTrue(ScripExtractor.matchesAnyScrip("CRUDEOIL"))
  }

  @Test
  fun testScripMatching_Derivatives() {
    assertTrue(ScripExtractor.matchesAnyScrip("NIFTY"))
    assertTrue(ScripExtractor.matchesAnyScrip("BANKNIFTY"))
    assertTrue(ScripExtractor.matchesAnyScrip("NIFTY24DEC25000CE"))
    assertTrue(ScripExtractor.matchesAnyScrip("GOLD24OCTFUT"))
    assertTrue(ScripExtractor.matchesAnyScrip("TCS24PE"))
  }

  @Test
  fun testScripCategorization() {
    assertEquals("Equity", ScripExtractor.getScripCategory("RELIANCE"))
    assertEquals("Equity", ScripExtractor.getScripCategory("ZOMATO"))
    assertEquals("Commodity", ScripExtractor.getScripCategory("GOLD"))
    assertEquals("Commodity", ScripExtractor.getScripCategory("CRUDEOIL"))
    assertEquals("Derivative", ScripExtractor.getScripCategory("NIFTY"))
    assertEquals("Derivative", ScripExtractor.getScripCategory("NIFTY24DEC25000CE"))
    assertEquals("Derivative", ScripExtractor.getScripCategory("GOLD24OCTFUT"))
  }

  @Test
  fun testFuzzyAndAliasExtraction() {
    val articleText = """
      Hindustan Aeronautics (HAL) and Bharat Electronics (BEL) rose ahead of DAC meeting.
      Other gainers included Bharat Dynamics (BDL), Mazagon Dock Shipbuilders (MAZDOCK), 
      and Paras Defence and Space Technologies (PARAS).
      Meanwhile, Reliance rose and Airtel shares fell.
      Some general text mentioning gold standard and LTCG tax increases.
    """.trimIndent()

    val extracted = ScripExtractor.extractScripsFromText(articleText)

    // Verify all specified stocks are extracted correctly
    assertTrue(extracted.contains("BEL"))
    assertTrue(extracted.contains("HAL"))
    assertTrue(extracted.contains("BDL"))
    assertTrue(extracted.contains("MAZDOCK"))
    assertTrue(extracted.contains("PARAS"))
    assertTrue(extracted.contains("RELIANCE"))
    assertTrue(extracted.contains("BHARTIARTL"))

    // Verify false positives are filtered out
    assertFalse(extracted.contains("LT")) // LTCG should not extract L&T (LT)
    assertFalse(extracted.contains("GOLD")) // lowercase "gold standard" should not extract GOLD commodity
  }

  @Test
  fun testSvpAndLicSiblingStockBehavior() {
    val article1 = """
      Our SVP of Marketing spoke about the brand growth strategy and the corporate outlook.
      The company shares are traded on NSE.
    """.trimIndent()
    val extracted1 = ScripExtractor.extractScripsFromText(article1)
    assertFalse(extracted1.contains("SVPGLOB")) // Should NOT match SVPGLOB

    val article2 = """
      LIC reported a massive increase in quarterly profit. Shares of LIC ended 2% higher.
    """.trimIndent()
    val extracted2 = ScripExtractor.extractScripsFromText(article2)
    assertTrue(extracted2.contains("LICI"))
    assertFalse(extracted2.contains("LICHSGFIN")) // Should NOT match LIC Housing Finance

    val article3 = """
      LIC Housing Finance reported stable quarterly results. Shares of LIC Housing Finance rose 3%.
    """.trimIndent()
    val extracted3 = ScripExtractor.extractScripsFromText(article3)
    assertTrue(extracted3.contains("LICHSGFIN"))
  }

  @Test
  fun testDisclaimerExclusion() {
    val article = """
      HDFC Bank reported strong quarterly earnings with significant margin growth.
      Disclaimer: The author or his relatives do not hold shares of Reliance Industries, SBI, or ICICI Bank. This is not a recommendation to buy or sell.
    """.trimIndent()
    val extracted = ScripExtractor.extractScripsFromText(article)
    assertTrue(extracted.contains("HDFCBANK"))
    assertFalse(extracted.contains("RELIANCE"))
    assertFalse(extracted.contains("SBIN"))
    assertFalse(extracted.contains("ICICIBANK"))
  }

  @Test
  fun testLivemintArticleStocks() {
    val article = """
      Buy or sell: Ganesh Dongre of Anand Rathi recommends 3 stocks to buy today.
      He recommends buying Bajaj Auto at current levels with a target of 10000.
      He also suggests KFin Technologies as a strong buy, and Tata Steel as a solid pick.
    """.trimIndent()
    val extracted = ScripExtractor.extractScripsFromText(article)
    assertTrue(extracted.contains("BAJAJ-AUTO"))
    assertTrue(extracted.contains("KFINTECH"))
    assertTrue(extracted.contains("TATASTEEL"))
  }
}
