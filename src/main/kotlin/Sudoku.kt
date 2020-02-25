package de.earley.actorSudoku

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel

class Sudoku(
	private val grid: Grid<Int?>
) {
	init {
		require(grid.size == 9 && grid.all { it.size == 9 })
	}

	operator fun get(x: Int, y: Int): Int? = grid[x, y]

	suspend fun forEach(action: suspend (x: Int, y: Int, number: Int) -> Unit) {
		(0..8).forEach { x ->
			(0..8).forEach { y ->
				val number = get(x, y)
				if (number != null) {
					action(x, y, number)
				}
			}
		}
	}
}

fun parseSudoku(line: String): Sudoku {
	val rows = line.chunked(9)
	require(rows.size == 9) { "Parse error: $line" }
	val grid = rows.map { row ->
		row.chunked(1).map { it.toIntOrNull() }.toTypedArray().also {
			require(it.size == 9) { "Parse error: $line" }
		}
	}.toTypedArray()
	return Sudoku(grid)
}


@ExperimentalCoroutinesApi
suspend fun main() {

	log("Loading sudoku")

	val sudokus = top95.lines().filter { it.isNotBlank() }.map { line ->
		parseSudoku(line)
	}

	val sudoku = sudokus[0]

	log("Starting solver")

	//TODO this bus is stupid. How do we do capacity?
	val bus = BroadcastChannel<SudokuMessage>(100_000)

	coroutineScope {
		setupView(bus)

		log("Started logging bus messages")
		val loggingChan = bus.openSubscription()
		launch {
			for (msg in loggingChan) {
				log(msg.toString())
			}
		}

		log("Starting cells")
		val cells = cellActors(bus)
		cells.forEach {
			it.start(this)
		}

		sudoku.forEach { x, y, number ->
			bus.send(SudokuMessage.IsNumber(Point(x, y), number, "SET"))
			delay(10)
		}

	}
}

fun log(msg: String) {
	console.log(msg)
}