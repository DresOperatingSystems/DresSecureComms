/* Copyright © 2026 The DresOS Foundation. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms.ui

import android.content.Context
import android.net.Uri
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.dresos.dressecurecomms.R
import com.dresos.dressecurecomms.data.SmsRepository

class TwoLineAdapter<T>(
    ctx: Context,
    private var items: List<T>,
    private val bind: (T) -> Triple<String, String, String>
) : BaseAdapter() {
    private val inflater = LayoutInflater.from(ctx)
    fun setItems(list: List<T>) { items = list; notifyDataSetChanged() }
    override fun getCount() = items.size
    override fun getItem(position: Int): T = items[position]
    override fun getItemId(position: Int) = position.toLong()
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val v = convertView ?: inflater.inflate(R.layout.item_two_line, parent, false)
        val (a, b, c) = bind(items[position])
        v.findViewById<TextView>(R.id.line1).text = a
        v.findViewById<TextView>(R.id.line2).text = b
        val time = v.findViewById<TextView>(R.id.time)
        time.text = c
        time.visibility = if (c.isEmpty()) View.GONE else View.VISIBLE
        return v
    }
}

class MessageAdapter(
    ctx: Context,
    private var items: List<SmsRepository.Msg>
) : BaseAdapter() {
    private val inflater = LayoutInflater.from(ctx)
    fun setItems(list: List<SmsRepository.Msg>) { items = list; notifyDataSetChanged() }
    override fun getCount() = items.size
    override fun getItem(position: Int): SmsRepository.Msg = items[position]
    override fun getItemId(position: Int) = position.toLong()
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val v = convertView ?: inflater.inflate(R.layout.item_message, parent, false)
        val m = items[position]
        val bubble = v.findViewById<LinearLayout>(R.id.bubble)
        val text = v.findViewById<TextView>(R.id.text)
        val image = v.findViewById<ImageView>(R.id.image)
        if (m.body.isNotBlank()) {
            text.visibility = View.VISIBLE
            text.text = m.body
        } else {
            text.visibility = View.GONE
        }
        if (m.imageUri != null) {
            image.visibility = View.VISIBLE
            try {
                image.setImageURI(Uri.parse(m.imageUri))
            } catch (e: Exception) {
                image.visibility = View.GONE
            }
        } else {
            image.visibility = View.GONE
            image.setImageDrawable(null)
        }
        bubble.setBackgroundResource(if (m.outgoing) R.drawable.bg_bubble_out else R.drawable.bg_bubble_in)
        val time = v.findViewById<TextView>(R.id.time)
        val stamp = com.dresos.dressecurecomms.util.TimeFmt.stamp(m.time)
        time.text = stamp
        time.visibility = if (stamp.isEmpty()) View.GONE else View.VISIBLE
        val lp = bubble.layoutParams as FrameLayout.LayoutParams
        lp.gravity = if (m.outgoing) Gravity.END else Gravity.START
        bubble.layoutParams = lp
        return v
    }
}
