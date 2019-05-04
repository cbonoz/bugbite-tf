/*
 *    Copyright (C) 2017 MINDORKS NEXTGEN PRIVATE LIMITED
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.bugbite.www.tf

import android.net.Uri
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentTransaction
import android.support.v7.app.AppCompatActivity
import com.bugbite.www.tf.fragments.CameraFragment
import timber.log.Timber

class MainActivity : AppCompatActivity(), CameraFragment.OnFragmentInteractionListener {


    private fun loadCameraFragment() {
        // Create new fragment and transaction
        val newFragment = CameraFragment.newInstance("", "")
        val transaction = supportFragmentManager.beginTransaction()

        // Replace whatever is in the fragment_container view with this fragment,
        // and add the transaction to the back stack
        transaction.replace(R.id.fragment_container, newFragment)
        transaction.addToBackStack(null)

        // Commit the transaction
        transaction.commit()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Setup activity helper classes.

        setContentView(R.layout.activity_main)
        Timber.d("onCreate, after setContentView")

        loadCameraFragment()

    }

    override fun onFragmentInteraction(uri: Uri) {}
}
