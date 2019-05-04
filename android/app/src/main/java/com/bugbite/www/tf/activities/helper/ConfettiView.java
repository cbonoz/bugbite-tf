package com.bugbite.www.tf.activities.helper;


import com.github.jinatonic.confetti.ConfettiManager;

public interface ConfettiView {

    ConfettiManager generateOnce();
    ConfettiManager generateStream();
    ConfettiManager generateInfinite();

}
