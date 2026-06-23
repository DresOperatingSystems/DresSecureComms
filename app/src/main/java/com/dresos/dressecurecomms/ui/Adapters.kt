/* Copyright © 2026 DresOS. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms.ui

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.TextView
import com.dresos.dressecurecomms.R
import com.dresos.dressecurecomms.data.SmsRepository

class TwoLineAdapter<T>(
    ctx: Context,
    private var items: List<T>,
    private val bind: (T) -> Pair<String, String>
) : BaseAdapter() {
    private val inflater = LayoutInflater.from(ctx)
    fun setItems(list: List<T>) { items = list; notifyDataSetChanged() }
    override fun getCount() = items.size
    override fun getItem(position: Int): T = items[position]
    override fun getItemId(position: Int) = position.toLong()
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val v = convertView ?: inflater.inflate(R.layout.item_two_line, parent, false)
        val (a, b) = bind(items[position])
        v.findViewById<TextView>(R.id.line1).text = a
        v.findViewById<TextView>(R.id.line2).text = b
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
        val bubble = v.findViewById<TextView>(R.id.bubble)
        bubble.text = m.body
        bubble.setBackgroundResource(if (m.outgoing) R.drawable.bg_bubble_out else R.drawable.bg_bubble_in)
        val lp = bubble.layoutParams as FrameLayout.LayoutParams
        lp.gravity = if (m.outgoing) Gravity.END else Gravity.START
        bubble.layoutParams = lp
        return v
    }
}
