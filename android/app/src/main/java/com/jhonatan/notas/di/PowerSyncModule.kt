package com.jhonatan.notas.di

import android.content.Context
import com.jhonatan.notas.data.powersync.notasSchema
import com.powersync.DatabaseDriverFactory
import com.powersync.PowerSyncDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PowerSyncModule {
    @Provides
    @Singleton
    fun providePowerSyncDatabase(
        @ApplicationContext context: Context,
    ): PowerSyncDatabase =
        PowerSyncDatabase(
            factory = DatabaseDriverFactory(context),
            schema = notasSchema,
        )
}
