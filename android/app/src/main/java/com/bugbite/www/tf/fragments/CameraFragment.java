package com.bugbite.www.tf.fragments;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.SoundPool;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.bugbite.www.tf.MainActivity;
import com.bugbite.www.tf.R;
import com.bugbite.www.tf.activities.helper.ConfettiView;
import com.bugbite.www.tf.utils.ClassificationTaskResult;
import com.bugbite.www.tf.utils.Classifier;
import com.bugbite.www.tf.utils.Classifier.Recognition;
import com.bugbite.www.tf.utils.ImageUtils;
import com.bugbite.www.tf.utils.Logger;
import com.camerakit.CameraKit;
import com.camerakit.CameraKitView;
import com.github.jinatonic.confetti.CommonConfetti;
import com.github.jinatonic.confetti.ConfettiManager;
import com.github.johnpersano.supertoasts.library.Style;
import com.github.johnpersano.supertoasts.library.SuperActivityToast;
import com.github.ybq.android.spinkit.SpinKitView;
import com.ramotion.foldingcell.FoldingCell;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import mehdi.sakout.fancybuttons.FancyButton;
import timber.log.Timber;

import static com.bugbite.www.tf.utils.ImageUtils.NO_BITE_RESULT;

public class CameraFragment extends Fragment implements ConfettiView {
    private static final Logger LOGGER = new Logger();

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";


    // for custom model refer to: https://github.com/tensorflow/tensorflow/issues/2883

    // Values for google's inception model (from the original paper findings).
    private static final int INPUT_SIZE = 224;

    // Default parameters.
    private Classifier.Model model = Classifier.Model.FLOAT;
    private Classifier.Device device = Classifier.Device.CPU;
    private int numThreads = 1;

    private static Classifier classifier; // Tensorflow classifier.

    private static Bitmap lastScreenShot;

    private boolean isMuted = true;

    private String mParam1;
    private String mParam2;

    private OnFragmentInteractionListener mListener;
    private FoldingCell fc;

    // Main layout views.
    private ViewGroup mainContainer;
    private LinearLayout resultLayout;
    private LinearLayout cameraActionLayout;

    // Scene components.
    private TextView textViewResult;
    private Button btnDetectObject;
    private Button btnToggleCamera;
    private SpinKitView loadingSpinner;
    private ImageView imageViewResult;
    private CameraKitView cameraKitView;

    private TextView result1;
    private TextView result2;
    private TextView result3;

    private FancyButton shareButton;

    private Dialog loadingDialog;


    public CameraFragment() {
        // Required empty public constructor
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        hideLoadingDialog();
        Observable.fromCallable(() -> {
            if (classifier != null) {
                classifier.close();
            }
            return classifier;
        })
                .subscribeOn(Schedulers.io())
                .subscribe();

    }
    private void showLoadingDialog() {
        loadingDialog = new MaterialDialog.Builder(getActivity())
                .title(R.string.processing)
                .content(ImageUtils.getRandomLoadingMessage())
                .progress(true, 0).show();
    }

    private void hideLoadingDialog() {
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
        }
    }

    private String getResultText(Recognition result) {
        return String.format(Locale.ENGLISH, "%s: %.1f", result.getTitle(),result.getConfidence());
    }


    private static final float CONFIDENCE_THRESHOLD = .3f;


    private Recognition renderClassificationResultDisplay(final List<Recognition> results) {
        renderResultList(results);


        for (Recognition result : results) {
            if (result.getConfidence() > CONFIDENCE_THRESHOLD) {
                // Show positive message/overlay to the user.
                if (NO_BITE_RESULT.equals(result.getTitle())) {
                    makeToast("Not a " + getString(R.string.target_item) + "!");
                    generateOnce().animate(); // generate confetti.
                } else {
                    makeToast("Looks like this could be a " + getString(R.string.target_item) + "!");
                }
                return result;
            }
        }

        if (results.isEmpty()) {
            makeToast(getString(R.string.empty_result_message));
        } else {
            makeToast("Not a " + getString(R.string.target_item));
        }
        // didn't successfully find the app object (current target: cookie).
        return null;
    }

    private void renderResultList(List<Classifier.Recognition> results) {
        result1.setText(getResultText(results.get(0)));
        result2.setText(getResultText(results.get(1)));
        result3.setText(getResultText(results.get(2)));
        fc.unfold(false);

        textViewResult.setText(results.toString()); // TODO: remove
    }

    public static CameraFragment newInstance(String param1, String param2) {
        CameraFragment fragment = new CameraFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        cameraKitView.onStart();
    }

    @Override
    public void onStop() {
        cameraKitView.onStop();
        super.onStop();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (classifier == null) {
            loadingSpinner.setVisibility(View.VISIBLE);
            initTensorFlowAndLoadModel()
                    .doOnComplete(() -> getActivity().runOnUiThread(this::doneLoadingClassifier))
                    .subscribeOn(Schedulers.io())
                    .subscribe();
        } else {
            btnDetectObject.setVisibility(View.VISIBLE);
        }
        cameraKitView.onResume();
    }

    @Override
    public void onPause() {
        cameraKitView.onStop();
        hideLoadingDialog();
        super.onPause();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setUpConfetti();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_camera, container, false);

        mainContainer = (ViewGroup) view.findViewById(R.id.container);
        cameraKitView = (CameraKitView) view.findViewById(R.id.camera);
        fc = (FoldingCell) view.findViewById(R.id.folding_cell);
        fc.fold(true);
        fc.setOnClickListener(v -> fc.fold(true));

        result1 = view.findViewById(R.id.result1);
        result2 = view.findViewById(R.id.result2);
        result3 = view.findViewById(R.id.result3);

        // cameraKitView.refreshDrawableState();

        cameraActionLayout = (LinearLayout) view.findViewById(R.id.cameraActionLayout);

        // Classification result view items.
        resultLayout = (LinearLayout) view.findViewById(R.id.resultLayout);
        imageViewResult = (ImageView) view.findViewById(R.id.imageViewResult);
        textViewResult = (TextView) view.findViewById(R.id.textViewResult);
        textViewResult.setMovementMethod(new ScrollingMovementMethod());
        shareButton = (FancyButton) view.findViewById(R.id.shareButton);

        btnToggleCamera = (Button) view.findViewById(R.id.btnToggleCamera);
        btnDetectObject = (Button) view.findViewById(R.id.btnDetectObject);
        loadingSpinner = (SpinKitView) view.findViewById(R.id.loadingSpinner);

        btnDetectObject.setVisibility(View.GONE);

        btnToggleCamera.setOnClickListener(v -> cameraKitView.toggleFacing());
        // set default facing direction.
        cameraKitView.setFacing(CameraKit.FACING_BACK);

        btnDetectObject.setOnClickListener(v -> {
            try {
                cameraKitView.captureImage((cameraKitView, picture) -> {
                    showLoadingDialog();
                    Timber.d("starting ClassifyImageTask with picture length " + picture.length);
                    new ClassifyImageTask().execute(picture);
                });
            } catch (Exception e) {
                makeToast(getString(R.string.camera_error));
                // Attempt to restart the camera after a failed image retrieval.
                cameraKitView.onStop();
                cameraKitView.onStart();
            }
        });

        return view;
    }

    // TODO: Rename method, update argument and hook method into UI event
    public void onButtonPressed(Uri uri) {
        if (mListener != null) {
            mListener.onFragmentInteraction(uri);
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnFragmentInteractionListener) {
            mListener = (OnFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    public interface OnFragmentInteractionListener {
        // TODO: Update argument type and name
        void onFragmentInteraction(Uri uri);
    }


    private void setUpConfetti() {
        Timber.d("setUpConfetti");
        final Resources res = getResources();
        goldDark = res.getColor(R.color.gold_dark);
        goldMed = res.getColor(R.color.gold_med);
        gold = res.getColor(R.color.gold);
        goldLight = res.getColor(R.color.gold_light);
        colors = new int[]{goldDark, goldMed, gold, goldLight};
    }

    protected int goldDark, goldMed, gold, goldLight;
    private int[] colors;

    @Override
    public ConfettiManager generateOnce() {
        return CommonConfetti.rainingConfetti(mainContainer, colors)
                .oneShot();
    }

    @Override
    public ConfettiManager generateStream() {
        return CommonConfetti.rainingConfetti(mainContainer, colors)
                .stream(3000);
    }

    @Override
    public ConfettiManager generateInfinite() {
        return CommonConfetti.rainingConfetti(mainContainer, colors)
                .infinite();
    }


    // ** Confetti Activity Logic Below ** //

    private class ClassifyImageTask extends AsyncTask<byte[], Integer, ClassificationTaskResult> {

        private static final long MIN_TASK_TIME_MS = 3000;

        private Bitmap scaledBitmap;
        private List<Classifier.Recognition> results;

        private String errorMessage = null;
        private long taskTime;

        protected ClassificationTaskResult doInBackground(byte[]... pictures) {
            final long startTime = System.currentTimeMillis();
            final int count = pictures.length;
            Timber.d("ClassifyImageTask with " + count + " byte array params (should be 1)");
            final byte[] picture = pictures[0];
            try {
                Bitmap bitmap = BitmapFactory.decodeByteArray(picture, 0, picture.length);
                scaledBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, false);
                if (classifier == null || classifier.getInterpreter() == null) { // last minute init.
                    initTensorFlowAndLoadModel().subscribe();
                }
                results = classifier.recognizeImage(scaledBitmap);
                Timber.d("classification results: " + results.toString());
            } catch (Exception e) {
                Timber.e("error in classification task: " + e.toString());
                if (e instanceof NullPointerException) {
                    errorMessage = "Oops, something went wrong, please reopen the app.";
                } else {
                    errorMessage = e.toString() + ". If the error occurs again, please reopen the app.";
                }
                return ClassificationTaskResult.FAIL;
            }

            taskTime = System.currentTimeMillis() - startTime;
            if (taskTime < MIN_TASK_TIME_MS) {
                try {
                    Thread.sleep(MIN_TASK_TIME_MS - taskTime);
                } catch (InterruptedException e) {
                    Timber.e("finishing task, interrupted during min task time sleep: " + e);
                }
            }

            return ClassificationTaskResult.SUCCESS;
        }

        private void showResultToast(final String message, final int color, final int resultIcon) {
            SuperActivityToast.create(getActivity(), new Style(), Style.TYPE_BUTTON)
                    .setButtonText("VIEW")
                    .setButtonIconResource(resultIcon)
                    .setOnButtonClickListener("show_result_page", null, (view, token) -> showResultPage(results))
                    .setProgressBarColor(Color.WHITE)
                    .setText(message)
                    .setDuration(Style.DURATION_LONG)
                    .setFrame(Style.FRAME_LOLLIPOP)
                    .setColor(getResources().getColor(color))
                    .setAnimations(Style.ANIMATIONS_POP).show();
        }

        protected void onPostExecute(final ClassificationTaskResult exitCode) {
            switch (exitCode) {
                case SUCCESS:
                    // We were able to successfully render a classification result on the taken image.
                    // If the foundResult is sufficiently confident, show success screen.
                    final Recognition foundResult = renderClassificationResultDisplay(results);
                    if (foundResult != null) {
                        shareButton.setBackgroundColor(getResources().getColor(R.color.md_green_500));
                        shareButton.setText(getString(R.string.share_success));
//                        showResultToast(getString(R.string.target_item) + "!", R.color.md_green_500, R.drawable.check_mark_75);
                    } else {
                        shareButton.setBackgroundColor(getResources().getColor(R.color.md_red_500));
                        shareButton.setText(getString(R.string.share_failure));
//                        showResultToast("Not a " + getString(R.string.target_item) + "!", R.color.md_red_500, R.drawable.x_mark_75);
                    }
                    resultLayout.setVisibility(View.VISIBLE);
                    imageViewResult.setImageBitmap(scaledBitmap);

                    shareButton.setOnClickListener(v -> {
                        // Image description used for the social share message.
                        final String imageDescription;
                        if (foundResult != null) {
                            imageDescription = "Successful find!";
                        } else {
                            imageDescription = "I failed";
                        }
                        Timber.d("hit share button, share message set to: " + imageDescription);
                        shareClassifierResult(imageDescription);
                    });
                    // Save the current state of the screen.
//                    lastScreenShot = takeShareableScreenshot();
                    break;
                case FAIL: // Software error - Unable to classify image.
                    resultLayout.setVisibility(View.GONE);
                    makeToast(errorMessage);
                    break;
            }
            hideLoadingDialog();
        }
    }

    private void showResultPage(List<Recognition> results) {

    }

    // ** Social Sharing Intent ** //

    private Bitmap takeShareableScreenshot() {
        // https://stackoverflow.com/questions/2661536/how-to-programmatically-take-a-screenshot-in-android
//        cameraActionLayout.setVisibility(View.GONE);
//        resultLayout.setVisibility(View.GONE);
        try {
            // create bitmap screen capture
            View v1 = getActivity().getWindow().getDecorView().getRootView();
            v1.setDrawingCacheEnabled(true);
            return Bitmap.createBitmap(v1.getDrawingCache());
        } catch (Throwable e) {
            // Several error may come out with file handling or OOM
            e.printStackTrace();
            makeToast(e.toString());
            Timber.e("Error capturing screenshot: " + e.toString());
            return null;
        }
//        finally {
//            cameraActionLayout.setVisibility(View.VISIBLE);
//            resultLayout.setVisibility(View.VISIBLE);
//        }
    }

    private void shareClassifierResult(final String imageDescription) {
        if (lastScreenShot == null) {
            makeToast(getString(R.string.taking_new_screenshot));
            lastScreenShot = takeShareableScreenshot();
        }

        String path = MediaStore.Images.Media.insertImage(getActivity().getContentResolver(), lastScreenShot, imageDescription, null);
        Uri uri = Uri.parse(path);

        Intent tweetIntent = new Intent(Intent.ACTION_SEND);
        tweetIntent.setType("image/jpeg");
        tweetIntent.putExtra(Intent.EXTRA_STREAM, uri);
        startActivity(Intent.createChooser(tweetIntent, getString(R.string.share_result_prompt)));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        cameraKitView.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private Observable<Classifier> initTensorFlowAndLoadModel() {
        return Observable.fromCallable(() -> {
            if (classifier != null) {
                LOGGER.d("Closing classifier.");
                classifier.close();
                classifier = null;
            }

            // Default to float model if quantized is not supported.
//            if (device == Classifier.Device.GPU && model == Classifier.Model.QUANTIZED) {
//                LOGGER.d("Creating float model: GPU doesn't support quantized models.");
//                model = Classifier.Model.FLOAT;
//            }

            try {
                LOGGER.d("Creating classifier (model=%s, device=%s, numThreads=%d)", model, device, numThreads);
                classifier = Classifier.create(getActivity(), device, numThreads);
                return classifier;
            } catch (IOException e) {
                LOGGER.e(e, "Failed to create classifier.");
            }
            return null;
        });
    }

    private void makeToast(final String msg) {
        if (getContext() != null) {
            Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
        }
    }

    private void doneLoadingClassifier() {
        getActivity().runOnUiThread(() -> {
            Timber.d("done loading classifier");
            loadingSpinner.setVisibility(View.GONE);
            btnDetectObject.setVisibility(View.VISIBLE);
        });
    }

    // ** Show or Hide the share button overlay on the current view.

    private void showShareView() {
        Timber.d("showShareView");
    }

    private void hideShareView() {
        Timber.d("hideShareView");
    }


}
