package de.earley.actorSudoku

typealias Grid<T> = Array<Array<T>>

data class Point(val x:Int, val y:Int)

inline fun <reified T> grid(rows: Int, cols: Int, init: (x:Int, y:Int) -> T): Grid<T> = Array(rows) { y ->
	Array(cols) { x ->
		init(x, y)
	}
}

operator fun <T> Grid<T>.get(point: Point): T {
	return this[point.y][point.x]
}


operator fun <T> Grid<T>.get(x: Int, y: Int): T {
	return this[y][x]
}
