/* 코틀린 이미지 로더 */

package com.example.myapplication

import android.os.AsyncTask
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.widget.EditText
import kotlinx.android.synthetic.main.activity_main.*
import org.jsoup.Jsoup
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.ImageView
import java.net.URL

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        var et_magic = findViewById<EditText>(R.id.editMagic)

        btn_test.setOnClickListener {
            var magic: String = et_magic.text.toString()
            txtStatus.setText(magic + "다운로드 중...")

            var html = urldownloadtask().execute()
            txtStatus.setText(html.get())
            imagedownloadtask(imgView).execute(html.get())
        }
    }

    class urldownloadtask : AsyncTask<String, String, String>() {
        override fun doInBackground(vararg params: String?): String? {
            var doc = Jsoup.connect("").get()
            return Parser.parseBlock(doc)
        }
    }

    class imagedownloadtask(img: ImageView) : AsyncTask<String, String, Bitmap>() {
        var ii = img

        override fun doInBackground(vararg params: String?): Bitmap? {
            var `in` = URL(params[0]).openStream()
            var ds = BitmapFactory.decodeStream(`in`)
            return ds
        }

        override fun onPostExecute(result: Bitmap?) {
            ii.setImageBitmap(result)
        }
    }
}
