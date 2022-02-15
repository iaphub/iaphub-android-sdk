package com.iaphub.example.store

import android.app.Application
import android.widget.Toast
import androidx.databinding.Observable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.iaphub.example.AppModel

class StoreViewModel(application: Application): AndroidViewModel(application), Observable {

    val app: AppModel = AppModel.get()
    val navigateToLogin = MutableLiveData<Boolean>(false)

    override fun removeOnPropertyChangedCallback(callback: Observable.OnPropertyChangedCallback?) {

    }

    override fun addOnPropertyChangedCallback(callback: Observable.OnPropertyChangedCallback?) {

    }

    fun restore() {
        this.app.restoreProducts() { err ->
            if (err != null) {
                Toast.makeText(this.getApplication(), "Restore failed, please try again later", Toast.LENGTH_SHORT).show()
            }
            else {
                Toast.makeText(this.getApplication(), "Restore successful", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun logout() {
        this.navigateToLogin.value = true
        this.app.logout()
    }

    fun logoutDone() {
        this.navigateToLogin.value = false
    }
}