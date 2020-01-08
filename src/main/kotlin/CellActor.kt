package de.earley.actorSudoku

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch

sealed class SudokuMessage {
	abstract val point: Point
	abstract val number: Int
	abstract val reason: String

	data class IsNumber(
		override val point: Point,
		override val number: Int,
		override val reason: String
	) : SudokuMessage()

	data class IsNotNumber(
		override val point: Point,
		override val number: Int,
		override val reason: String
	): SudokuMessage()
}

// https://www.sudokuwiki.org

@ExperimentalCoroutinesApi
data class CellActor(
	val bus: BroadcastChannel<SudokuMessage>,
	val point: Point
) {
	private val state = InProgressSudoku()

	private val blockPoint: Point = Point(
		(point.x / 3) * 3,
		(point.y / 3) * 3
	)

	//TODO y wing?, swordfish !

	private val chan: ReceiveChannel<SudokuMessage> = bus.openSubscription()

	fun start(scope: CoroutineScope) {
		val messages = chan
		scope.launch {
			for (msg in messages) {

				state.update(msg)

				if (isMe(msg)) {
					when (msg) {
						is SudokuMessage.IsNumber -> setNumber(msg.number)
						is SudokuMessage.IsNotNumber -> checkIfOnlyOneOption()
					}
				} else {

					if (msg is SudokuMessage.IsNumber && isInReach(msg)) deduceNotNumber(msg.number, "Removed by ${msg.point}")

					if (msg is SudokuMessage.IsNumber && isMe(msg)) setNumber(msg.number)

					if (isBlock(msg)) {
						checkIfLastInBlock()
						checkAllInBlockAligned()
						checkTupleInBlock()
					}
					if (isRow(msg)) {
						checkIfLastInRow()
						checkTupleInRow()
						checkXWingRow() // TODO x wing col
					}
					if (isCol(msg)) {
						checkIfLastInCol()
						checkTupleInCol()
					}
				}

			}
		}
	}

	private suspend fun checkIfOnlyOneOption() {
		if (done) {
			deduceNumber(state[point].single(), "last option")
		}
	}

	private suspend fun checkIfLastInBlock() = checkIfLastIn(
		blockPoint.x..(blockPoint.x + 2),
		blockPoint.y..(blockPoint.y + 2)
	)

	private suspend fun checkIfLastInRow() = checkIfLastIn(point.x..point.x, 0..8)
	private suspend fun checkIfLastInCol() = checkIfLastIn(point.x..point.x, 0..8)

	private suspend fun checkIfLastIn(xRange: IntRange, yRange: IntRange) {
		if (done) return
		for (number in 1..9) {
			if (!possible(number)) continue

			if (count(xRange, yRange, number) == 1) {
				log("Deduced number $number in $point, b.c. it  was the last option in block/row/col")
				deduceNumber(number, "last in row/col/block")
				return
			}
		}
	}

	private fun count(xRange: IntRange, yRange: IntRange, number: Int): Int = xRange.sumBy { x ->
		yRange.count { y ->
			state[x, y].contains(number)
		}
	}
	private fun count(xRange: List<Int>, yRange: List<Int>, number: Int): Int = xRange.sumBy { x ->
			yRange.count { y ->
			state[x, y].contains(number)
		}
	}

	private suspend fun checkAllInBlockAligned() {
		if (done) return

		// if all the possible numbers in this cell occur only in the same row/col then project that out
		state[point].forEach { number ->
			checkRowAlignedInBlock(number)
			checkColAlignedInBlock(number)
		}
	}

	private suspend fun checkColAlignedInBlock(number: Int) {
		val otherColCount = count(
			(blockPoint.x..(blockPoint.x + 2)).filter { it != point.x },
			(blockPoint.y..(blockPoint.y + 2)).toList(),
			number
		)
		if (otherColCount == 0) {
			for (y in 0..8) {
				if (y >= blockPoint.y && y <= blockPoint.y + 2) continue

				if (state[point.x, y].contains(number)) {
					storeAndSend(SudokuMessage.IsNotNumber(Point(point.x, y), number, "Col aligned in block"))
				}
			}
		}
	}

	private suspend fun checkRowAlignedInBlock(number: Int) {
		val otherRowCount = count(
			(blockPoint.x..(blockPoint.x + 2)).toList(),
			(blockPoint.y..(blockPoint.y + 2)).filter { it != point.y },
			number
		)
		if (otherRowCount == 0) {
			for (x in 0..8) {
				if (x >= blockPoint.x && x <= blockPoint.x + 2) continue

				if (state[x, point.y].contains(number)) {
					storeAndSend(SudokuMessage.IsNotNumber(Point(x, point.y), number, "Row aligned in block"))
				}
			}
		}
	}

	private suspend fun checkTupleInRow() = checkTupleIn(0..8, point.y .. point.y)
	private suspend fun checkTupleInCol() = checkTupleIn(point.x.. point.x, 0..8)
	private suspend fun checkTupleInBlock() = checkTupleIn(blockPoint.x .. (blockPoint.x+2), blockPoint.y .. (blockPoint.y+2))



	private suspend fun checkTupleIn(xRange: IntRange, yRange: IntRange) = checkTuple(xRange, yRange, state[point])

//	private suspend fun checkSubsetTupleIn(xRange: IntRange, yRange: IntRange) {
//		if (done) return
//
//		val myPossible = state[point]
//		val myCount = myPossible.size
//
//		val subsets = myPossible.powerset().groupBy { it.size }
//
//		for (count in 2..maxOf(myCount, 4)) { //heuristic cap
//			subsets[count]?.forEach { tuple ->
//				checkTuple(xRange, yRange, tuple)
//			}
//		}
//	}

	private suspend fun checkTuple(xRange: IntRange, yRange: IntRange, tuple: Set<Int>) {
		if (done) return

		val count = tuple.size

		// look for others with the same possible numbers
		val pointsInRange = xRange.flatMap { x -> yRange.map { y -> Point(x, y) } }
		val samePossible = pointsInRange.filter { p ->
			val set = state[p]
			/*set.size <= count && */ tuple.containsAll(set)
		}

		if (samePossible.size == count) {
			// others are impossible
			for (p in pointsInRange) {
				if (p in samePossible) {
					// check if it has other things apart from the tuple,
					// if so, remove it
					state[p].filterNot { tuple.contains(it) }.forEach { number ->
						storeAndSend(SudokuMessage.IsNotNumber(p, number, "Tuple interior of size $count by $point"))
					}
				} else {
					state[p].filter { tuple.contains(it) }.forEach { number ->
						storeAndSend(SudokuMessage.IsNotNumber(p, number, "Tuple projection of size $count by $point"))
					}
				}
			}
		}
	}

	private suspend fun checkXWingRow() {
		if (done) return

		val myPossible = state[point]

		myPossible.forEach { number ->
			if (count(0..8, point.y .. point.y, number) == 2) {
				// potential for an x wing
				val otherInRow = Point(
					(0..8).find { it != point.x && state[it, point.y].contains(number) }!!,
					point.y
				)
				// now scan other rows in this column for partners
				for (row in 0..8) {
					if (row == point.y) continue

					val colPoint = Point(point.x, row)
					val colOther = Point(otherInRow.x, row)

					if (state[colPoint].contains(number) && state[colOther].contains(number)) {
						// getting closer to the x wing

						if (count(0..8, row..row, number) == 2) {
							// X Wing!
							// the columns now cannot be that number
							for (elimRow in 0..8) {
								if (elimRow == row || elimRow == point.y) continue

								val pointsToTarget = listOf(Point(point.x, elimRow), Point(otherInRow.x, elimRow))

								for (p in pointsToTarget) {
									if (state[p].contains(number)) {
										storeAndSend(SudokuMessage.IsNotNumber(p, number, "X-Wing by $point, $otherInRow, $colPoint, $colOther"))
									}
								}
							}

						}
					}

				}
			}
		}
	}

	private suspend fun deduceNumber(number: Int, reason: String) {
		setNumber(number)
		storeAndSend(SudokuMessage.IsNumber(point, number, reason))
	}

	private suspend fun deduceNotNumber(number: Int, reason: String) {
		storeAndSend(SudokuMessage.IsNotNumber(point, number, reason))
	}

	private fun setNumber(number: Int) {
		// we are done with messages
		chan.cancel()
	}

	private fun possible(number: Int) = state[point].contains(number)

	private fun isMe(msg: SudokuMessage) = msg.point.x == point.x && msg.point.y == point.y
	private fun isBlock(msg: SudokuMessage) = msg.point.x / 3 == point.x / 3 && msg.point.y / 3 == point.y / 3
	private fun isRow(msg: SudokuMessage) = msg.point.x != point.x && msg.point.y == point.y
	private fun isCol(msg: SudokuMessage) = msg.point.x == point.x && msg.point.y != point.y
	private fun isInReach(msg: SudokuMessage) = isBlock(msg) || isCol(msg) || isRow(msg)


	private val done: Boolean
		get() =	state[point].size == 1

	private suspend fun storeAndSend(msg: SudokuMessage) {
		//TODO check state before to make sure it makes sense?
		state.update(msg)
		bus.send(msg)
	}

}

private fun <T> Collection<T>.powerset(): Sequence<Set<T>> =
	if (isEmpty()) sequenceOf(emptySet())
	else sequence {
		val head = first()
		val tailP = drop(1).powerset()
		yieldAll(tailP)
		yieldAll(tailP.map { it + head })
	}

fun cellActors(bus: BroadcastChannel<SudokuMessage>): List<CellActor> {
	return (0..8).flatMap { x ->
		(0..8).map { y ->
			CellActor(bus, Point(x, y))
		}
	}
}