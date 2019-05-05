package com.bugbite.www.tf.fragments

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.app.ActivityCompat
import android.support.v4.app.Fragment
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

import com.afollestad.materialdialogs.MaterialDialog
import com.bugbite.www.tf.activities.MainActivity
import com.bugbite.www.tf.R
import com.bugbite.www.tf.activities.helper.ConfettiView
import com.bugbite.www.tf.models.BugBiteResult
import com.bugbite.www.tf.utils.ClassificationTaskResult
import com.bugbite.www.tf.utils.Classifier
import com.bugbite.www.tf.utils.Classifier.Recognition
import com.bugbite.www.tf.utils.ImageUtils
import com.bugbite.www.tf.utils.Logger
import com.camerakit.CameraKit
import com.camerakit.CameraKitView
import com.github.jinatonic.confetti.CommonConfetti
import com.github.jinatonic.confetti.ConfettiManager
import com.github.johnpersano.supertoasts.library.Style
import com.github.johnpersano.supertoasts.library.SuperActivityToast
import com.github.ybq.android.spinkit.SpinKitView
import com.ramotion.foldingcell.FoldingCell

import java.io.IOException
import java.util.Locale

import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import mehdi.sakout.fancybuttons.FancyButton
import timber.log.Timber

import com.bugbite.www.tf.utils.ImageUtils.NO_BITE_RESULT
import com.google.gson.Gson
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import org.apache.commons.lang3.StringUtils
import org.koin.android.ext.android.inject
import java.util.concurrent.TimeUnit


class CameraFragment : Fragment(), ConfettiView {

    private val EXT_STORAGE_CODE = 3
    private var lastResults: List<Classifier.Recognition>? = null
    // Default parameters.
    private val model = Classifier.Model.FLOAT
    private val device = Classifier.Device.CPU
    private val numThreads = 1

    private var mListener: OnFragmentInteractionListener? = null
    private lateinit var fc: FoldingCell

    // Main layout views.
    private var mainContainer: ViewGroup? = null
    private var resultLayout: LinearLayout? = null
    private var cameraActionLayout: LinearLayout? = null

    // Scene components.
    private var textViewResult: TextView? = null
    private var btnDetectObject: Button? = null
    private var btnToggleCamera: Button? = null
    private var loadingSpinner: SpinKitView? = null
    private var imageViewResult: ImageView? = null
    private var cameraKitView: CameraKitView? = null

    private var result1: TextView? = null
    private var result2: TextView? = null
    private var result3: TextView? = null

    private var shareButton: FancyButton? = null

    private var loadingDialog: Dialog? = null

    protected var goldDark: Int = 0
    protected var goldMed: Int = 0
    protected var gold: Int = 0
    protected var goldLight: Int = 0
    private var colors: IntArray? = null

    private var lastScreenShot: Bitmap? = null

    val gson: Gson by inject()

    override fun onDestroy() {
        super.onDestroy()
        hideLoadingDialog()
        Observable.fromCallable<Classifier> {
            if (classifier != null) {
                classifier!!.close()
            }
            classifier
        }
                .subscribeOn(Schedulers.io())
                .subscribe()

    }

    private fun showLoadingDialog() {
        loadingDialog = MaterialDialog.Builder(activity!!)
                .title(R.string.processing)
                .content(ImageUtils.randomLoadingMessage)
                .progress(true, 0).show()
    }

    private fun hideLoadingDialog() {
        if (loadingDialog != null && loadingDialog!!.isShowing) {
            loadingDialog!!.dismiss()
        }
    }

    private fun getResultText(result: Recognition): String {
        return String.format(Locale.ENGLISH, "%s: %.1f%%",
                StringUtils.capitalize(result.title),
                result.confidence * 100)
    }

    private fun renderClassificationResultDisplay(results: List<Recognition>?, scaledBitmap: Bitmap?): BugBiteResult {
        lastResults = results
        renderResultList()

        val biteResult: BugBiteResult = analyzeResult(results)
        shareButton!!.visibility = View.INVISIBLE

        when (biteResult) {
            BugBiteResult.IS_BITE -> {
                // we have at least one confirming bite result in this case.
                val color = resources.getColor(R.color.md_red_500)
                showResultToast("Looks like this could be a ${results!!.get(0).title} bite!", color, R.drawable.x_mark_75)
                delayResultButton(color, getString(R.string.see_result))
            }
            BugBiteResult.NOT_BITE -> {
                val color = resources.getColor(R.color.md_green_500)
                showResultToast("Not a " + getString(R.string.target_item) + "!", color, R.drawable.check_mark_75)
                generateOnce().animate() // generate confetti.
                delayResultButton(color, getString(R.string.share_photo))
            }
            BugBiteResult.NOT_SURE -> {
                makeToast(getString(R.string.empty_result_message))
                val color = resources.getColor(R.color.green)
                showResultButton(color, getString(R.string.share_photo))
            }
        }

        resultLayout!!.visibility = View.VISIBLE

        imageViewResult!!.setImageBitmap(scaledBitmap)

        shareButton!!.setOnClickListener { v ->
            // Image description used for the social share message.
            when (biteResult) {
                BugBiteResult.NOT_BITE -> {
                    shareClassifierResult()
                }
                BugBiteResult.IS_BITE -> {
                    showResultPage(results)
                }
                BugBiteResult.NOT_SURE -> {
                    shareClassifierResult()
                }
            }
            // Timber.d("hit share button, share message set to: $imageDescription")
        }
        return biteResult
    }

    private fun delayResultButton(color: Int, s: String) {
        Completable.timer(RESULT_TOAST_DURATION, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
                .subscribe {
                    showResultButton(color, s)
                }
    }

    private fun showResultButton(color: Int, s: String) {
        shareButton!!.setBackgroundColor(color)
        shareButton!!.setText(s)
        shareButton!!.visibility = View.VISIBLE
    }

    private fun analyzeResult(results: List<Recognition>?): BugBiteResult {
        for (result in results.orEmpty()) {
            if (result.confidence > CONFIDENCE_THRESHOLD) {
                // Show positive message/overlay to the user.
                if (NO_BITE_RESULT == result.title) {
                    return BugBiteResult.NOT_BITE
                } else {
                    return BugBiteResult.IS_BITE
                }
            }
        }

        return BugBiteResult.NOT_SURE
    }

    private fun renderResultList() {
        if (lastResults == null || lastResults!!.isEmpty()) {
            makeToast("Take a photo by clicking 'Detect'!")
            return
        }
        val results = lastResults!!
        result1!!.text = "${getResultText(results[0])} <- most likely"
        result2!!.text = getResultText(results[1])
        result3!!.text = getResultText(results[2])
        fc.unfold(false)
    }

    override fun onStart() {
        super.onStart()
        cameraKitView!!.onStart()
    }

    override fun onStop() {
        cameraKitView!!.onStop()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (classifier == null) {
            loadingSpinner!!.visibility = View.VISIBLE
            initTensorFlowAndLoadModel()
                    .doOnComplete { activity!!.runOnUiThread { this.doneLoadingClassifier() } }
                    .subscribeOn(Schedulers.io())
                    .subscribe()
        } else {
            btnDetectObject!!.visibility = View.VISIBLE
        }
        cameraKitView!!.onResume()
    }

    override fun onPause() {
        cameraKitView!!.onStop()
        hideLoadingDialog()
        super.onPause()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        setUpConfetti()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_camera, container, false)

        mainContainer = view.findViewById<View>(R.id.container) as ViewGroup
        cameraKitView = view.findViewById<View>(R.id.camera) as CameraKitView
        fc = view.findViewById<View>(R.id.folding_cell) as FoldingCell
        fc.fold(true)
        fc.setOnClickListener { v -> fc.toggle(false) }

        result1 = view.findViewById(R.id.result1)
        result2 = view.findViewById(R.id.result2)
        result3 = view.findViewById(R.id.result3)

        // cameraKitView.refreshDrawableState();

        cameraActionLayout = view.findViewById<View>(R.id.cameraActionLayout) as LinearLayout

        // Classification result view items.
        resultLayout = view.findViewById<View>(R.id.resultLayout) as LinearLayout
        imageViewResult = view.findViewById<View>(R.id.imageViewResult) as ImageView
        textViewResult = view.findViewById<View>(R.id.textViewResult) as TextView
        textViewResult!!.movementMethod = ScrollingMovementMethod()
        shareButton = view.findViewById<View>(R.id.shareButton) as FancyButton

        btnToggleCamera = view.findViewById<View>(R.id.btnToggleCamera) as Button
        btnDetectObject = view.findViewById<View>(R.id.btnDetectObject) as Button
        loadingSpinner = view.findViewById<View>(R.id.loadingSpinner) as SpinKitView

        btnDetectObject!!.visibility = View.GONE

        btnToggleCamera!!.setOnClickListener { v -> cameraKitView!!.toggleFacing() }
        // set default facing direction.
        cameraKitView!!.facing = CameraKit.FACING_BACK

        btnDetectObject!!.setOnClickListener { v ->
            try {
                cameraKitView!!.captureImage { cameraKitView, picture ->
                    showLoadingDialog()
                    Timber.d("starting ClassifyImageTask with picture length " + picture.size)
                    ClassifyImageTask().execute(picture)
                }
            } catch (e: Exception) {
                makeToast(getString(R.string.camera_error))
                // Attempt to restart the camera after a failed image retrieval.
                cameraKitView!!.onStop()
                cameraKitView!!.onStart()
            }
        }

        return view
    }

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        if (context is OnFragmentInteractionListener) {
            mListener = context
        } else {
            throw RuntimeException(context!!.toString() + " must implement OnFragmentInteractionListener")
        }
    }

    override fun onDetach() {
        super.onDetach()
        mListener = null
    }

    interface OnFragmentInteractionListener {
        fun onFragmentInteraction(uri: Uri)
    }


    private fun setUpConfetti() {
        Timber.d("setUpConfetti")
        val res = resources
        goldDark = res.getColor(R.color.gold_dark)
        goldMed = res.getColor(R.color.gold_med)
        gold = res.getColor(R.color.gold)
        goldLight = res.getColor(R.color.gold_light)
        colors = intArrayOf(goldDark, goldMed, gold, goldLight)
    }

    override fun generateOnce(): ConfettiManager {
        return CommonConfetti.rainingConfetti(mainContainer!!, colors!!)
                .oneShot()
    }

    override fun generateStream(): ConfettiManager {
        return CommonConfetti.rainingConfetti(mainContainer!!, colors!!)
                .stream(3000)
    }

    override fun generateInfinite(): ConfettiManager {
        return CommonConfetti.rainingConfetti(mainContainer!!, colors!!)
                .infinite()
    }

    private fun showResultToast(message: String, color: Int, resultIcon: Int) {
        SuperActivityToast.create(activity!!, Style(), Style.TYPE_BUTTON)
                .setProgressBarColor(Color.WHITE)
                .setIconResource(resultIcon)
                .setText(message)
                .setDuration(RESULT_TOAST_DURATION.toInt())
                .setFrame(Style.FRAME_LOLLIPOP)
                .setColor(color)
                .setAnimations(Style.ANIMATIONS_POP).show()
    }

    // ** Confetti Activity Logic Below ** //

    private inner class ClassifyImageTask : AsyncTask<ByteArray, Int, ClassificationTaskResult>() {

        private var scaledBitmap: Bitmap? = null
        private var results: List<Recognition>? = null

        private var errorMessage: String? = null
        private var taskTime: Long = 0

        override fun doInBackground(vararg pictures: ByteArray): ClassificationTaskResult {
            val startTime = System.currentTimeMillis()
            val count = pictures.size
            Timber.d("ClassifyImageTask with $count byte array params (should be 1)")
            val picture = pictures[0]
            try {
                val bitmap = BitmapFactory.decodeByteArray(picture, 0, picture.size)
                lastScreenShot = bitmap
                scaledBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, false)
                if (classifier == null || classifier!!.interpreter == null) { // last minute init.
                    initTensorFlowAndLoadModel().subscribe()
                }
                results = classifier!!.recognizeImage(scaledBitmap!!)
                Timber.d("classification results: " + results!!.toString())
            } catch (e: Exception) {
                Timber.e("error in classification task: " + e.toString())
                if (e is NullPointerException) {
                    errorMessage = "Oops, something went wrong, please reopen the app."
                } else {
                    errorMessage = e.toString() + ". If the error occurs again, please reopen the app."
                }
                return ClassificationTaskResult.FAIL
            }

            taskTime = System.currentTimeMillis() - startTime
            if (taskTime < MIN_TASK_TIME_MS) {
                try {
                    Thread.sleep(MIN_TASK_TIME_MS - taskTime)
                } catch (e: InterruptedException) {
                    Timber.e("finishing task, interrupted during min task time sleep: $e")
                }

            }

            return ClassificationTaskResult.SUCCESS
        }


        override fun onPostExecute(exitCode: ClassificationTaskResult) {
            when (exitCode) {
                ClassificationTaskResult.SUCCESS -> {
                    // We were able to successfully render a classification result on the taken image.
                    // If the foundResult is sufficiently confident, show success screen.
                    val foundResult = renderClassificationResultDisplay(results, scaledBitmap)

                }
                ClassificationTaskResult.FAIL // Software error - Unable to classify image.
                -> {
                    resultLayout!!.visibility = View.GONE
                    makeToast(errorMessage)
                }
            }// Save the current state of the screen.
            //                    lastScreenShot = takeShareableScreenshot();
            hideLoadingDialog()
        }

    }

    private fun showResultPage(results: List<Recognition>?) {
        (activity as MainActivity).loadFragment(BiteResultFragment.newInstance(gson, results))
    }

    // ** Social Sharing Intent ** //

    private fun takeShareableScreenshot(): Bitmap? {
        // https://stackoverflow.com/questions/2661536/how-to-programmatically-take-a-screenshot-in-android
        //        cameraActionLayout.setVisibility(View.GONE);
        //        resultLayout.setVisibility(View.GONE);
        try {
            // create bitmap screen capture
            val v1 = activity!!.window.decorView.rootView
            v1.isDrawingCacheEnabled = true
            return Bitmap.createBitmap(v1.drawingCache)
        } catch (e: Throwable) {
            // Several error may come out with file handling or OOM
            e.printStackTrace()
            makeToast(e.toString())
            Timber.e("Error capturing screenshot: " + e.toString())
            return null
        }

        //        finally {
        //            cameraActionLayout.setVisibility(View.VISIBLE);
        //            resultLayout.setVisibility(View.VISIBLE);
        //        }
    }

    private fun shareClassifierResult() {
            if (lastScreenShot == null) {
                makeToast(getString(R.string.taking_new_screenshot))
                lastScreenShot = takeShareableScreenshot()
            }

        try {
            val path = MediaStore.Images.Media.insertImage(activity!!.contentResolver, lastScreenShot, getString(R.string.share_text), null)
            val uri = Uri.parse(path)

            val tweetIntent = Intent(Intent.ACTION_SEND)
            tweetIntent.type = "image/jpeg"
            tweetIntent.putExtra(Intent.EXTRA_STREAM, uri)
            startActivity(Intent.createChooser(tweetIntent, getString(R.string.share_result_prompt)))
        } catch (e : Exception) {
            ActivityCompat.requestPermissions(activity as Activity,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    EXT_STORAGE_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == EXT_STORAGE_CODE) {
            shareClassifierResult()
            return
        }
        cameraKitView!!.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun initTensorFlowAndLoadModel(): Observable<Classifier> {
        return Observable.fromCallable {
            if (classifier != null) {
                LOGGER.d("Closing classifier.")
                classifier!!.close()
                classifier = null
            }

            // Default to float model if quantized is not supported.
            //            if (device == Classifier.Device.GPU && model == Classifier.Model.QUANTIZED) {
            //                LOGGER.d("Creating float model: GPU doesn't support quantized models.");
            //                model = Classifier.Model.FLOAT;
            //            }

            try {
                LOGGER.d("Creating classifier (model=%s, device=%s, numThreads=%d)", model, device, numThreads)
                classifier = Classifier.create(activity as Activity, device, numThreads)
            } catch (e: IOException) {
                LOGGER.e(e, "Failed to create classifier.")
            }

            classifier
        }
    }

    private fun makeToast(msg: String?) {
        if (context != null) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun doneLoadingClassifier() {
        activity!!.runOnUiThread {
            Timber.d("done loading classifier")
            loadingSpinner!!.visibility = View.GONE
            btnDetectObject!!.visibility = View.VISIBLE
        }
    }

    companion object {
        private val LOGGER = Logger()

        // Selected supported size for google's inception model (from the original paper findings).
        private val INPUT_SIZE = 224

        private var classifier: Classifier? = null // Tensorflow Float Mobile Net classifier.

        val MIN_TASK_TIME_MS: Long = 3000

        private val CONFIDENCE_THRESHOLD = .50f // percentage confidence threshold for significant result.

        val RESULT_TOAST_DURATION = Style.DURATION_MEDIUM.toLong()

        fun newInstance(): CameraFragment {
            val fragment = CameraFragment()
            val args = Bundle()
//            args.putString(ARG_PARAM1, param1)
            fragment.arguments = args
            return fragment
        }
    }

}
