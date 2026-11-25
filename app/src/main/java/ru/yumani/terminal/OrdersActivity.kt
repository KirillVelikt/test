package ru.yumani.terminal

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ListView
import android.widget.SimpleAdapter
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

class OrdersActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_orders)

        val json = intent.getStringExtra("json") ?: ""
        if (json.isEmpty()) { finish(); return }

        val obj = JSONObject(json)
        val arr = obj.optJSONArray("items")
        val data = ArrayList<HashMap<String, String>>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val it = arr.getJSONObject(i)
                val m = HashMap<String, String>()
                m["name"] = it.optString("n", "товар")
                m["qty"] = "x " + it.optInt("q", 1).toString()
                m["price"] = it.optInt("p", 0).toString() + " ₽"
                data.add(m)
            }
        }
        findViewById<ListView>(R.id.lvItems).adapter = SimpleAdapter(
            this, data, R.layout.item_order,
            arrayOf("name", "qty", "price"),
            intArrayOf(R.id.tvItemName, R.id.tvItemQty, R.id.tvItemPrice)
        )
        findViewById<TextView>(R.id.tvTotal).text = "итого: " + obj.optInt("total", 0).toString() + " ₽"
        findViewById<Button>(R.id.btnPay).setOnClickListener {
            startActivity(
                Intent(this, PaymentActivity::class.java)
                    .putExtra("amount", obj.optInt("total", 0))
                    .putExtra("json", json)
            )
        }
    }
}
