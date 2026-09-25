package cu.rge.cartera

import android.app.AlertDialog
import android.content.Context
import android.widget.Button
import android.widget.TextView
import java.text.DecimalFormat

class CalculatorDialog(private val context: Context) {
    private var currentValue = StringBuilder("0")
    private var operator: Char? = null
    private var previousValue = 0.0
    private var isNewOperation = true
    private val decimalFormat = DecimalFormat("#.##")
    
    private lateinit var display: TextView
    private lateinit var dialog: AlertDialog
    
    fun show(onResult: (Double) -> Unit) {
        val dialogView = android.view.LayoutInflater.from(context).inflate(R.layout.dialog_calculator, null)
        display = dialogView.findViewById<TextView>(R.id.calculatorDisplay)
        
        dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        // Setup number buttons
        val buttons = mapOf(
            R.id.button0 to "0",
            R.id.button1 to "1",
            R.id.button2 to "2",
            R.id.button3 to "3",
            R.id.button4 to "4",
            R.id.button5 to "5",
            R.id.button6 to "6",
            R.id.button7 to "7",
            R.id.button8 to "8",
            R.id.button9 to "9"
        )
        
        buttons.forEach { (id, value) ->
            dialogView.findViewById<Button>(id).setOnClickListener {
                appendNumber(value)
            }
        }
        
        // Setup operator buttons
        val operators = mapOf(
            R.id.buttonPlus to "+",
            R.id.buttonMinus to "-",
            R.id.buttonMultiply to "×",
            R.id.buttonDivide to "÷"
        )
        
        operators.forEach { (id, op) ->
            dialogView.findViewById<Button>(id).setOnClickListener {
                setOperator(op[0])
            }
        }
        
        // Setup special buttons
        dialogView.findViewById<Button>(R.id.buttonClear).setOnClickListener {
            clear()
        }
        
        dialogView.findViewById<Button>(R.id.buttonDecimal).setOnClickListener {
            appendDecimal()
        }
        
        dialogView.findViewById<Button>(R.id.buttonEquals).setOnClickListener {
            calculate()
        }
        
        dialogView.findViewById<Button>(R.id.buttonOk).setOnClickListener {
            val result = currentValue.toString().toDoubleOrNull() ?: 0.0
            onResult(result)
            dialog.dismiss()
        }
        
        dialogView.findViewById<Button>(R.id.buttonCancel).setOnClickListener {
            dialog.dismiss()
        }
        
        updateDisplay()
        dialog.show()
    }
    
    private fun appendNumber(num: String) {
        if (isNewOperation) {
            currentValue = StringBuilder()
            isNewOperation = false
        }
        if (currentValue.toString() == "0" && num != ".") {
            currentValue = StringBuilder(num)
        } else {
            currentValue.append(num)
        }
        updateDisplay()
    }
    
    private fun appendDecimal() {
        if (isNewOperation) {
            currentValue = StringBuilder("0")
            isNewOperation = false
        }
        if (!currentValue.contains(".")) {
            currentValue.append(".")
        }
        updateDisplay()
    }
    
    private fun setOperator(op: Char) {
        if (operator != null && !isNewOperation) {
            calculate()
        }
        previousValue = currentValue.toString().toDoubleOrNull() ?: 0.0
        operator = op
        isNewOperation = true
    }
    
    private fun calculate() {
        if (operator == null || isNewOperation) return
        
        val current = currentValue.toString().toDoubleOrNull() ?: 0.0
        val result = when (operator) {
            '+' -> previousValue + current
            '-' -> previousValue - current
            '×' -> previousValue * current
            '÷' -> if (current != 0.0) previousValue / current else 0.0
            else -> current
        }
        
        currentValue = StringBuilder(decimalFormat.format(result))
        operator = null
        isNewOperation = true
        updateDisplay()
    }
    
    private fun clear() {
        currentValue = StringBuilder("0")
        operator = null
        previousValue = 0.0
        isNewOperation = true
        updateDisplay()
    }
    
    private fun updateDisplay() {
        display.text = currentValue.toString()
    }
}
