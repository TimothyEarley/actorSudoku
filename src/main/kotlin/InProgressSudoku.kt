package de.earley.actorSudoku

class InProgressSudoku {

	private val grid: Grid<MutableSet<Int>> = grid(9, 9) { _,_->
		(1..9).toMutableSet()
	}

	operator fun get(x: Int, y: Int): Set<Int> = grid[y][x]
	operator fun get(point: Point): Set<Int> = grid[point]

	fun update(msg: SudokuMessage) {
		when (msg) {
			is SudokuMessage.IsNumber -> {
				val cell = grid[msg.point]
				cell.clear()
				cell += msg.number
			}
			is SudokuMessage.IsNotNumber -> {
				val cell = grid[msg.point]
				cell -= msg.number
			}
		}
	}


}