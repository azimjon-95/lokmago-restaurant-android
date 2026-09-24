package uz.lokmago.restaurant

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import uz.lokmago.restaurant.alert.Notifier

@HiltAndroidApp
class LokmaGoApp : Application() {
    @Inject lateinit var notifier: Notifier
    override fun onCreate() {
        super.onCreate()
        notifier.ensureChannels()
    }
}
