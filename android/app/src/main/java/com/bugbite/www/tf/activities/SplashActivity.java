package com.bugbite.www.tf.activities;

import android.content.Intent;
import android.graphics.Color;

import com.daimajia.androidanimations.library.Techniques;
import com.bugbite.www.tf.MainActivity;
import com.bugbite.www.tf.R;

import wail.splacher.com.splasher.lib.SplasherActivity;
import wail.splacher.com.splasher.models.SplasherConfig;
import wail.splacher.com.splasher.utils.Const;

public class SplashActivity extends SplasherActivity {

    @Override
    public void initSplasher(SplasherConfig config) {
        config.setReveal_start(Const.START_TOP_LEFT) // anitmation type ..
                //---------------
                .setAnimationDuration(3000) // Reveal animation duration ..
                //---------------
                .setLogo(R.drawable.logo_175) // logo src..
                .setLogo_animation(Techniques.BounceIn) // logo animation ..
                .setAnimationLogoDuration(2000) // logo animation duration ..
                .setLogoWidth(500) // logo width ..
                //---------------
                .setTitle(getString(R.string.app_name)) // title ..
                .setTitleColor(Color.parseColor("#ffffff")) // title color ..
                .setTitleAnimation(Techniques.Bounce) // title animation ( from Android View Animations ) ..
                .setTitleSize(24) // title text size ..
                //---------------
                .setSubtitle("cookies...") // subtitle
                .setSubtitleColor(Color.parseColor("#ffffff")) // subtitle color
                .setSubtitleAnimation(Techniques.FadeIn) // subtitle animation (from Android View Animations) ..
                .setSubtitleSize(16); // subtitle text size ..
                //---------------
    }

    @Override
    public void onSplasherFinished() {
        // tart main activity after animation finished.
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish(); // use to wrap up the current activity so the user can't hit back to it.
    }

}
