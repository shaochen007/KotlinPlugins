package com.cy.kotlinplugins.main

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.cy.cost.annotation.Cost
import com.cy.kotlinplugins.R

class MainActivity : AppCompatActivity() {

    @Cost
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

    }
}
