package com.omrmaster.app

import android.app.Application
import com.omrmaster.app.data.local.OmrDatabase
import com.omrmaster.app.data.repository.OmrRepository
import com.omrmaster.app.omr.OmrImageProcessor

class OmrApplication : Application() {
    val database by lazy { OmrDatabase.getInstance(this) }
    val repository by lazy { OmrRepository(database.examDao(), database.studentResultDao()) }

    override fun onCreate() {
        super.onCreate()
        OmrImageProcessor.ensureInitialized()
    }
}
