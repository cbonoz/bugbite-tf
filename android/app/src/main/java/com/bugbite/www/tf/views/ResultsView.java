package com.bugbite.www.tf.views;


import com.bugbite.www.tf.utils.Classifier.Recognition;

import java.util.List;

public interface ResultsView {
    public void setResults(final List<Recognition> results);
}