package com.example.beerapp.data.remote

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.example.beerapp.data.IBeerApi
import com.example.beerapp.data.local.BeerDatabase
import com.example.beerapp.data.local.BeerEntity
import com.example.beerapp.data.mappers.toBeerEntity
import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.io.IOException

@OptIn(ExperimentalPagingApi::class)
class BeerRemoteMediator(
    private val beerDatabase: BeerDatabase,
    private val beerApi: IBeerApi
) : RemoteMediator<Int, BeerEntity>() {
    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, BeerEntity>
    ): MediatorResult {
        return try {
            val loadKey = when (loadType) { // loadKey is only the current page (it is just an integer)
                LoadType.REFRESH -> 1
                LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
                LoadType.APPEND -> {
                    val lastItem = state.lastItemOrNull()
                    if (lastItem == null) {
                        1
                    } else {
                        (lastItem.id / state.config.pageSize) + 1
                    }
                }
            }
            delay(2000)
            val beers = beerApi.getBeers(
                page = loadKey,
                pageCount = state.config.pageSize
            )
            /**
             * Writing the result into the database.
             * Since we are executing multiple sql statements one after another we want to be sure that
             *  they are only be called when every single statement succeeded. Either all calls will succeed
             *  or none will succeed.
             */
            beerDatabase.withTransaction {
                if (loadType == LoadType.REFRESH) {
                    beerDatabase.dao.clearAll()
                }
                val beerEntities = beers.map { it.toBeerEntity() }
                beerDatabase.dao.upsertAll(beerEntities)
            }
            MediatorResult.Success(
                // api will simply return an empty list if the requested page is too high
                endOfPaginationReached = beers.isEmpty()
            )
        } catch (exc: IOException) {
            MediatorResult.Error(exc)
        } catch (exc: HttpException) {
            MediatorResult.Error(exc)
        }
    }
}