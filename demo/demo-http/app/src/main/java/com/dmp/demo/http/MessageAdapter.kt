package com.dmp.demo.http

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView

class MessageAdapter : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {
    
    val messages = mutableListOf<ChatMessage>()
    
    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        if (message.role == "user") {
            holder.cardUser.visibility = View.VISIBLE
            holder.cardAI.visibility = View.GONE
            holder.tvUserMessage.text = message.content
        } else {
            holder.cardUser.visibility = View.GONE
            holder.cardAI.visibility = View.VISIBLE
            holder.tvAIMessage.text = message.content
        }
    }
    
    override fun getItemCount() = messages.size
    
    class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardUser: CardView = view.findViewById(R.id.cardUser)
        val cardAI: CardView = view.findViewById(R.id.cardAI)
        val tvUserMessage: TextView = view.findViewById(R.id.tvUserMessage)
        val tvAIMessage: TextView = view.findViewById(R.id.tvAIMessage)
    }
}