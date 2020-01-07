package de.earley.actorSudoku

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.html.*
import kotlinx.html.dom.create
import kotlinx.html.js.onClickFunction
import org.w3c.dom.HTMLTableElement
import org.w3c.dom.events.Event
import kotlin.browser.document
import kotlin.browser.window

fun CoroutineScope.setupView(bus: BroadcastChannel<SudokuMessage>) {
	val view = View(bus, this)

	window.onload = {
		view.init()
		launch {
			view.listen()
		}
	}
}

class View(
	private val bus: BroadcastChannel<SudokuMessage>,
	private val scope: CoroutineScope
) {

	private val chan = bus.openSubscription()

	private val sudoku = InProgressSudoku()
	private val tableId = "sudoku"

	private val table: HTMLTableElement
		get() = document.getElementById(tableId) as HTMLTableElement

	fun init() {
		refresh()
	}

	private fun refresh() {
		val newTable = document.create.table {
			id = tableId
			(0..8).forEach { y ->
				tr {
					(0..8).forEach { x ->
						td(classes = "mainCell") {
							val cell = sudoku[x, y].toList()
							if (cell.size == 1) {
								p { +cell[0].toString() }
							} else {
								fun n(i: Int) = if (cell.contains(i)) "$i" else " "
								table {
									(0..2).forEach { i ->
										tr {
											(1..3).forEach { di ->
												val number = 3 * i + di
												td {
													+n(number)
													onClickFunction = click(x, y, number)
												}
											}
										}
									}
								}
							}
						}
					}
				}
			}
		}

		table.replaceWith(newTable)
	}

	private fun click(x: Int, y: Int, number: Int): (Event) -> Unit = {
		log("click $x/$y with $number")
		scope.launch {
			bus.send(SudokuMessage.IsNumber(Point(x, y), number))
		}
	}

	suspend fun listen() {
		for (msg in chan) {
			sudoku.update(msg)
			log("Refreshing view")
			refresh()
			delay(1)
		}
	}

}