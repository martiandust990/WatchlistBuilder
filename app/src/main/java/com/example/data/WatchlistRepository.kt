package com.example.data

import kotlinx.coroutines.flow.Flow

class WatchlistRepository(private val watchlistDao: WatchlistDao) {

    val allWatchlists: Flow<List<WatchlistEntity>> = watchlistDao.getAllWatchlists()

    fun getScripsForWatchlist(watchlistId: Int): Flow<List<ScripEntity>> {
        return watchlistDao.getScripsForWatchlist(watchlistId)
    }

    suspend fun addWatchlist(name: String): Int {
        val entity = WatchlistEntity(name = name)
        return watchlistDao.insertWatchlist(entity).toInt()
    }

    suspend fun addScrip(watchlistId: Int, scripName: String) {
        val scrip = ScripEntity(watchlistId = watchlistId, scripName = scripName)
        watchlistDao.insertScrip(scrip)
    }

    suspend fun deleteWatchlist(watchlistId: Int) {
        watchlistDao.deleteWatchlist(watchlistId)
    }

    suspend fun deleteScrip(scripId: Int) {
        watchlistDao.deleteScrip(scripId)
    }

    suspend fun deleteScripsByName(watchlistId: Int, scripName: String) {
        watchlistDao.deleteScripsByName(watchlistId, scripName)
    }

    suspend fun updateScrip(scrip: ScripEntity) {
        watchlistDao.insertScrip(scrip)
    }
}
