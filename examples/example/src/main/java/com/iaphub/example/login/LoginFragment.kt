package com.iaphub.example.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.navigation.fragment.NavHostFragment
import com.iaphub.example.R
import com.iaphub.example.databinding.FragmentLoginBinding

class LoginFragment: Fragment() {

    private lateinit var model: LoginViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding: FragmentLoginBinding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_login, container, false
        )
        // Init model
        val application = requireNotNull(this.activity).application
        this.model = LoginViewModel(application)
        binding.model = this.model
        binding.lifecycleOwner = viewLifecycleOwner
        // Listen to navigateToStore property
        this.model.navigateToStore.observe(viewLifecycleOwner, Observer { value ->
            if (value == true) {
                this.navigateToStore()
                this.model.loginDone()
            }
        })

        return binding.root
    }

    private fun navigateToStore() {
        val action = LoginFragmentDirections.actionLoginFragmentToStoreFragment()
        NavHostFragment.findNavController(this).navigate(action)
    }
}