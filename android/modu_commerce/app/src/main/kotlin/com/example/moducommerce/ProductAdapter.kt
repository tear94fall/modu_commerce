package com.example.moducommerce

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView

/** 상품 카드 목록. 이미지 주소가 없는 상품은 플레이스홀더(bg_product_thumbnail)가 그대로 보인다. 카드를 누르면 onClick. */
class ProductAdapter(private val onClick: (Product) -> Unit) : RecyclerView.Adapter<ProductAdapter.Holder>() {

    private val items = mutableListOf<Product>()

    fun submit(list: List<Product>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.product_row, parent, false), onClick)

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    class Holder(view: View, private val onClick: (Product) -> Unit) : RecyclerView.ViewHolder(view) {

        private val image: ShapeableImageView = view.findViewById(R.id.product_image)
        private val name: TextView = view.findViewById(R.id.product_name)
        private val price: TextView = view.findViewById(R.id.product_price)
        private val description: TextView = view.findViewById(R.id.product_description)

        fun bind(product: Product) {
            itemView.setOnClickListener { onClick(product) }
            name.text = product.name ?: ""
            price.text = product.priceLabel()
            description.text = product.description ?: ""
            description.visibility = if (product.description.isNullOrBlank()) View.GONE else View.VISIBLE

            val url = product.imageUrlOrNull()
            if (url == null) {
                // 재활용된 뷰에 남은 사진을 지워 플레이스홀더가 보이게 한다.
                Glide.with(image).clear(image)
                image.setImageDrawable(null)
            } else {
                Glide.with(image).load(url).centerCrop().into(image)
            }
        }
    }
}
