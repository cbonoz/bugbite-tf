package com.bugbite.www.tf.activities.helper


import com.github.jinatonic.confetti.ConfettiManager

interface ConfettiView {

    fun generateOnce(): ConfettiManager
    fun generateStream(): ConfettiManager
    fun generateInfinite(): ConfettiManager

}
