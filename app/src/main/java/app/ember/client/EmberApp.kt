package app.ember.client

import android.app.Application

class EmberApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Td.start(this)
    }
}
