package com.bugbite.www.tf.fragments

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Button
import com.bugbite.www.tf.BugApplication

import com.bugbite.www.tf.R
import com.bugbite.www.tf.utils.Classifier
import com.google.gson.Gson
import org.koin.android.ext.android.inject
import org.koin.core.context.GlobalContext.get
import com.google.gson.reflect.TypeToken
import mehdi.sakout.fancybuttons.FancyButton
import android.webkit.WebSettings




private const val BITE_RESULT_ARG = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Activities that contain this fragment must implement the
 * [BiteResultFragment.OnFragmentInteractionListener] interface
 * to handle interaction events.
 * Use the [BiteResultFragment.newInstance] factory method to
 * create an instance of this fragment.
 *
 */
class BiteResultFragment() : Fragment() {
    // TODO: Rename and change types of parameters
    private var listener: OnFragmentInteractionListener? = null

    lateinit var backButton: FancyButton
    lateinit var webView: WebView

    val gson: Gson by inject()

    lateinit var classifierResults: List<Classifier.Recognition>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            classifierResults = gson.fromJson(it.getString(BITE_RESULT_ARG),
                    object : TypeToken<List<Classifier.Recognition>>() {}.type)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(R.layout.fragment_bite_result, container, false)

        backButton = v.findViewById(R.id.back_button)
        backButton.setOnClickListener {
            activity?.onBackPressed()
        }
        webView = v.findViewById(R.id.webview)
        val webSettings = webView.getSettings()
        webSettings.setJavaScriptEnabled(true)

        webView.loadUrl("https://g.co/kgs/C9YzZW")
        return v
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnFragmentInteractionListener) {
            listener = context
        } else {
            throw RuntimeException(context.toString() + " must implement OnFragmentInteractionListener")
        }
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }


    interface OnFragmentInteractionListener {
        fun onFragmentInteraction(uri: Uri)
    }

    companion object {

        @JvmStatic
        fun newInstance(gson: Gson, results: List<Classifier.Recognition>?): BiteResultFragment {
            return BiteResultFragment().apply {
                arguments = Bundle().apply {
                    putString(BITE_RESULT_ARG, gson.toJson(results))
                }
            }
        }
    }
}
