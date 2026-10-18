package com.dmp.android.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.dmp.android.databinding.ItemMessageBinding

/**
 * 消息数据类
 */
data class ChatMessage(
    val role: String, // "user" or "assistant"
    val content: String
)

/**
 * 消息适配器
 */
class MessageAdapter : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {
    
    val messages = mutableListOf<ChatMessage>()
    
    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }
    
    fun updateLastMessage(content: String) {
        if (messages.isNotEmpty()) {
            val lastIndex = messages.size - 1
            messages[lastIndex] = messages[lastIndex].copy(content = content)
            notifyItemChanged(lastIndex)
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemMessageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MessageViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(messages[position])
    }
    
    override fun getItemCount(): Int = messages.size
    
    class MessageViewHolder(
        private val binding: ItemMessageBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(message: ChatMessage) {
            if (message.role == "user") {
                binding.cardUser.visibility = android.view.View.VISIBLE
                binding.cardAssistant.visibility = android.view.View.GONE
                binding.textUserMessage.text = message.content
            } else {
                binding.cardUser.visibility = android.view.View.GONE
                binding.cardAssistant.visibility = android.view.View.VISIBLE
                binding.textAssistantMessage.text = message.content
            }
        }
    }
}