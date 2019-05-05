package com.bugbite.www.tf.fragments

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView

import com.bugbite.www.tf.R
import com.bugbite.www.tf.utils.Classifier
import com.google.gson.Gson
import org.koin.android.ext.android.inject
import com.google.gson.reflect.TypeToken
import mehdi.sakout.fancybuttons.FancyButton
import android.webkit.WebViewClient
import android.widget.TextView
import android.widget.Toast

private const val BITE_RESULT_ARG = "param1"

/**
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
    lateinit var resultTitle: TextView

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
        webView = v.findViewById(R.id.webview)
        resultTitle = v.findViewById(R.id.result_title)

        backButton.setOnClickListener {
            activity?.onBackPressed()
        }

        webView.webViewClient = BugBiteWebViewClient()
        val webSettings = webView.getSettings()
        webSettings.setJavaScriptEnabled(true)

        if (classifierResults.isEmpty()) {
            Toast.makeText(context, context!!.getString(R.string.no_results), Toast.LENGTH_SHORT).show()
            return v
        }

        val topResult = classifierResults[0]
        webView.loadUrlForTopResult(activity!!, topResult)
        resultTitle.text = topResult.title

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

    private inner class BugBiteWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            view.loadUrl(url)
            return true
        }
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

private fun WebView.loadUrlForTopResult(context: Context, topResult: Classifier.Recognition) {


    val urlMap = mapOf(
            "spider" to "https://g.co/kgs/C9YzZW",
            "tick" to "https://www.mayoclinic.org/first-aid/first-aid-tick-bites/basics/art-20056671",
            "bee" to "https://g.co/kgs/XdXKNX",
            "mosquito" to "https://www.mayoclinic.org/diseases-conditions/mosquito-bites/diagnosis-treatment/drc-20375314"
    )

    val resultUrl = urlMap.get(topResult.title)?:context.getString(R.string.no_result_url)
    loadUrl(resultUrl)
}
