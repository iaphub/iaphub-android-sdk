package com.iaphub.example.login

import android.app.Application
import android.widget.Toast
import androidx.databinding.Bindable
import androidx.databinding.Observable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.iaphub.example.AppModel

class LoginViewModel(application: Application): AndroidViewModel(application), Observable {

    private val app: AppModel = AppModel.get()

    @Bindable
    val userId = MutableLiveData<String>("")

    val navigateToStore = MutableLiveData<Boolean>(false)

    fun loginWithUserId() {
        val userId = this.userId.value
        if (userId != null && userId.isNotEmpty()) {
            this.app.login(userId) { err ->
                if (err == null) {
                    this.navigateToStore.value = true
                }
                else {
                    Toast.makeText(this.getApplication(), "Login failed, please try again later", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun loginAnonymously() {
        this.app.loginAnonymously()
        this.navigateToStore.value = true
    }

    fun loginDone() {
        this.navigateToStore.value = false
    }

    override fun removeOnPropertyChangedCallback(callback: Observable.OnPropertyChangedCallback?) {

    }

    override fun addOnPropertyChangedCallback(callback: Observable.OnPropertyChangedCallback?) {

    }
}