package com.bugbite.www.tf

import android.app.Application
import com.bugbite.www.tf.utils.Classifier
import com.bugbite.www.tf.utils.ClassifierFloatMobileNet
import com.google.gson.Gson
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

class BugApplication : Application() {

    override fun onCreate(){
        super.onCreate()
        startKoin {
            androidContext(this@BugApplication)
            val myModule = module {
                single { Gson() }
            }
            modules(myModule)
        }
    }
}