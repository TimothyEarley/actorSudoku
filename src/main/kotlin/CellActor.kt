package de.earley.actorSudoku

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch

sealed class SudokuMessage {
	abstract val point: Point
	abstract val number: Int

	data class IsNumber(override val point: Point, override val number: Int) : SudokuMessage()
	data class IsNotNumber(override val point: Point, override val number: Int): SudokuMessage()
}

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

	//TODO projection

	private val chan: ReceiveChannel<SudokuMessage> = bus.openSubscription()

	fun start(scope: CoroutineScope) {
		val messages = chan
		scope.launch {
			for (msg in messages) {

				state.update(msg)

				if (isMe(msg)) {
					when (msg) {
						is SudokuMessage.IsNumber -> setNumber(msg.number)
						is SudokuMessage.IsNotNumber -> { /* TODO check if only one option left */ }
					}
				} else {

					if (msg is SudokuMessage.IsNumber && isInReach(msg)) deduceNotNumber(msg.number)

					if (msg is SudokuMessage.IsNumber && isMe(msg)) setNumber(msg.number)

					//TODO multple deductions?
					if (isBlock(msg)) {
						checkIfLastInBlock()
						checkAllInBlockAligned()
						checkTupleInBlock()
					}
					if (isRow(msg)) {
						checkIfLastInRow()
						checkTupleInRow()
					}
					if (isCol(msg)) {
						checkIfLastInCol()
						checkTupleInCol()
					}
				}

			}
		}
	}

	private suspend fun checkIfLastInBlock() = checkIfLastIn(
		blockPoint.x..(blockPoint.x + 2),
		blockPoint.y..(blockPoint.y + 2)
	)

	private suspend fun checkIfLastInRow() = checkIfLastIn(point.x..point.x, 0..8)
	private suspend fun checkIfLastInCol() = checkIfLastIn(point.x..point.x, 0..8)

	private suspend fun checkIfLastIn(xRange: IntRange, yRange: IntRange) {
		for (number in 1..9) {
			if (!possible(number)) continue

			if (count(xRange, yRange, number) == 1) {
				log("Deduced number $number in $point, b.c. it  was the last option in block/row/col")
				deduceNumber(number)
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
					storeAndSend(SudokuMessage.IsNotNumber(Point(point.x, y), number))
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
					storeAndSend(SudokuMessage.IsNotNumber(Point(x, point.y), number))
				}
			}
		}
	}

	private suspend fun checkTupleInRow() = checkTupleIn(0..8, point.y .. point.y)
	private suspend fun checkTupleInCol() = checkTupleIn(point.x.. point.x, 0..8)
	private suspend fun checkTupleInBlock() = checkTupleIn(blockPoint.x .. (blockPoint.x+2), blockPoint.y .. (blockPoint.y+2))



	private suspend fun checkTupleIn(xRange: IntRange, yRange: IntRange) {
		if (done) return

		val myPossible = state[point]
		val myCount = myPossible.size
		// look for others with the same possible numbers

		// heuristic:
		if (myCount > 3) return

		// don't mess with only one option left deduction
		if (myCount == 1) return

		val pointsInRange = xRange.flatMap { x -> yRange.map { y -> Point(x, y) } }
		val samePossible = pointsInRange.filter { p ->
			val set = state[p]
			set.size <= myCount && myPossible.containsAll(set)
		}

		if (samePossible.size == myCount) {
			// others are impossible
			for (p in pointsInRange) {
				if (p in samePossible) continue

				state[p].filter { myPossible.contains(it) }.forEach {  number ->
					log("Found tuple of size $myCount consisting of $samePossible and number=$number. Target: $p")
					storeAndSend(SudokuMessage.IsNotNumber(p, number))
				}
			}
		}
	}

	private suspend fun deduceNumber(number: Int) {
		setNumber(number)
		storeAndSend(SudokuMessage.IsNumber(point, number))
	}

	private suspend fun deduceNotNumber(number: Int) {
		storeAndSend(SudokuMessage.IsNotNumber(point, number))
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

fun cellActors(bus: BroadcastChannel<SudokuMessage>): List<CellActor> {
	return (0..8).flatMap { x ->
		(0..8).map { y ->
			CellActor(bus, Point(x, y))
		}
	}
}