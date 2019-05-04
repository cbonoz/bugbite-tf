package com.bugbite.www.tf.views


import com.bugbite.www.tf.utils.Classifier.Recognition

interface ResultsView {
    fun setResults(results: List<Recognition>)
}