package chessSolution;

import java.util.ArrayList;
import java.util.Random;
/*

 * Moves
 * 1) general eating detection (OK)
 * 2) white8P OK
 * 3) white & black pawns 2 UP (only once if not moved before) OK
 * 4) white & black pawns eating diagonal OK
 * 5) blocking movement (blocked piece)  OK
 * 6) rooks movement (blocked piece)   OK
 * 7) bishops movement (blocked piece, color)  OK
 * 8) queens movement (blocked piece)  OK
 * 9) knights movement (jump over)  OK
 * 10) check detection, forced moves (list of checked squares->not allowed moves for king) -> array of checked squares (BLACK/WHITE)
 * 11) checkmate detection (not causing it i.e. not moving to a check)
 * 12) castling (two ways, short & long)
 * 13) ohestalyönti
 * 14) repetition (three fold etc.) & other end rules e.g. stalemate
 * 15) promotion (pawn to queen OR knight (only if chessmate threat) )
 * 16) list of allowed moves
 * 17) choosing random move from the allowed list
 * 18) from random to chessmate, listing the path and updating UI
 * 19) position valuation rules (how to get points)
 * 20) implementing rules
 * 21) testing rules with a game, iteration -> 19)
 * 22) general collision detection for all pieces (bishop)
 *
 * - Collision detection: +1 ... -1 (rook, bishop; queen, pawn+2)
 * - Threats: all existing pieces until collision = CHECK
 * - Check control: is there a threat towards the king AFTER THIS move?
 * - threat = can EAT (next turn)
 * - en passe: immediately after pawn has moved two!
 * - check: block check OR move king -> move accepted IF no check anymore after this move
 * - possible moves: move towards LEFTMOST, TOPMOST, DOWNMOST, RIGHTMOST (and diagonals) until there is (value != 0)
 *
 * CHECK all possible moves in theory (1.6.2016)
 * select the best move from the possible moves
 * check & mate condition, ohestalyönti & promotion, castling
 * https://en.wikipedia.org/wiki/Castling
 * the capture can only be made on the move immediately after the opposing pawn makes the double-step move; otherwise the right to capture it en passant is lost.
 *
 */
public class ChessKernel {

	/**
	 * @throws InterruptedException
	 * @param args
	 */
	public static void main(String[] args) throws InterruptedException {

		ChessKernel chessKernel = new ChessKernel();
		chessKernel.printCurrentCheckBoardPosition();

		for (int i = 0; i < 2; ++i) {
			chessKernel.autoMove();
		}
	}

	private static final boolean DEBUG = false;
	private ArrayList<Object> possibleMovesWhite = new ArrayList<>();
	private ArrayList<Object> possibleMovesBlack = new ArrayList<>();
	private ArrayList<Object> whiteThreadsAgainstBlackKing = new ArrayList<>(); // for check
	private ArrayList<Object> blackThreadsAgainstWhiteKing = new ArrayList<>();

	private int moveNumber = 0;
	private Random rand = new Random();
	private boolean printPossibleMoves = true;

	private boolean whiteTurn = true; // TODO, change to whiteTurn
	private boolean afterBlackMove = false; // only for threads
	private boolean afterWhiteMove = false;
	private String blackKing = "45"; // TODO, FIXME
	private String whiteKing = "85";



	// must define special move codes for castling (e.g. 0000-0003) and
	// ohestalyönnit (detect the move and remove the other PAWN, check column)
	private int board[][] = new int[9][9];

	private boolean blackChecked = false;
	private boolean whiteChecked = false;

	private boolean openCheckAgainstWhite = false;
	private boolean openCheckAgainstBlack = false;

	public ChessKernel() {
		setBoard();
	}


	private void setBoard() {

		// BLACK pieces
		/*
		 * for (int c = 1; c < 9; ++c) { board[2][c] = -1; } board[1][1] = -5;
		 * board[1][8] = -5; board[1][2] = -3; board[1][7] = -3; board[1][3] =
		 * -4; board[1][6] = -4; board[1][4] = -9;
		 */
		board[4][5] = -6; // KING
		board[5][5] = -9; // queen
		// WHITE pieces
		/*
		 * for (int c = 1; c < 9; ++c) { board[7][c] = 1; } board[8][1] = 5;
		 * board[8][8] = 5; board[8][2] = 3; board[8][7] = 3; board[8][3] = 4;
		 * board[8][6] = 4;
		 */
		board[7][5] = 3;
		board[6][4] = 1;
		//board[2][1] = 1;

		int whiteKingRow = 8;
		int whiteKingCol = 5;

		board[whiteKingRow][whiteKingCol] = 6; // king
		//board[8][2] = 3; // knight
		this.whiteKing = "" + whiteKingRow + whiteKingCol;
	}

	/**
	 * Move automatically without user input
	 */
	private void autoMove() {

		if (whiteTurn) {
			
			
			System.out.print("\n" + ++moveNumber + "."
					+ " Possible moves (\u2B1CWHITE\u2B1C): \n");
			possibleMovesWhite.clear();
			afterWhiteMove = false;
			if (whiteChecked) {
				System.err.println("handle check white");
				System.exit(-1);
			}

		} else {
			System.out.print("\n" + ++moveNumber + "."
					+ " Possible moves (\u2B1BBLACK\u2B1B): \n");
			possibleMovesBlack.clear();
			afterBlackMove = false;
			if (blackChecked) {
				System.err.println("handle check black");
				System.exit(-1);
			}
		}

		// white or black turn
		for (int i = 1; i < 9; ++i) {
			for (int j = 1; j < 9; ++j) {
				int pieceValue = board[i][j]; // identify the piece (black/white
												// & type)
				if (pieceValue == 0)
					continue; // skip empty squares

				checkAllPossibleMoves(pieceValue, i, j); //FIXME
			}
		}
		int numberOfMoves;

		if (whiteTurn) {
			numberOfMoves = possibleMovesWhite.size();
		} else {
			numberOfMoves = possibleMovesBlack.size();
			
		}

		if (numberOfMoves <= 0) {
			System.err.println("check mate, n = " + numberOfMoves);
			System.exit(-1); // TODO
		}

		// do the move
		int fromRow;
		int fromCol;
		int toRow;
		int toCol;

		int fromPiece;
		String piece;

		openCheckAgainstWhite = true;
		openCheckAgainstBlack = true;
		int[] randomMove;

		ArrayList<Object> removalMoves = new ArrayList<>(); // moves to be removed from possible moves due to a threat

		if (whiteTurn) { // avoshakki -> reverse the move
			randomMove = possibleMoves(true, whiteKing, possibleMovesWhite, blackThreadsAgainstWhiteKing, removalMoves, board);
		}  else { //BLACK TURN	
			randomMove = possibleMoves(false, blackKing, possibleMovesBlack, whiteThreadsAgainstBlackKing, removalMoves, board);					
		} //eof black turn

		// do the move§
		fromRow = randomMove[0];
		fromCol = randomMove[1];
		toRow = randomMove[2];
		toCol = randomMove[3];

		fromPiece = board[fromRow][fromCol];
		//toPiece = board[toRow][toCol];

		piece = pieceConverter(fromPiece, 0, 0);

		openCheckAgainstWhite  = false;
		openCheckAgainstBlack  = false;

		if (whiteTurn) {
			possibleMovesWhite.clear();
			whiteThreadsAgainstBlackKing.clear();
			afterWhiteMove = true;
		} else {
			possibleMovesBlack.clear();
			blackThreadsAgainstWhiteKing.clear();
			afterBlackMove = true;
		}

		printPossibleMoves = false; // possible moves (threads) for the next move, thus not printable as current moves
		// white or black turn
		for (int i = 1; i < 9; ++i) {
			for (int j = 1; j < 9; ++j) {
				int pieceValue = board[i][j]; // identify the piece (black/white
												// & type)
				if (pieceValue == 0) {
					continue; // skip empty squares
				} else {
					checkAllPossibleMoves(pieceValue, i, j);
				}
						
			}
		}
		printPossibleMoves = true;
		System.out.println("\n  \u2192Selecting a RANDOM move: " + piece + "("
				+ randomMove[0] + randomMove[1] + randomMove[2] + randomMove[3] + ")");


			board[randomMove[0]][randomMove[1]] = 0;	//TODO, is this OK FIXME


			if (randomMove[2] == 1 && fromPiece == 1) {
				System.err.println("White pawn promoted to queen!");
				board[randomMove[2]][randomMove[3]] = 9; // promotion to queen
			} else {
				board[randomMove[2]][randomMove[3]] = fromPiece;
			}

			if (randomMove[2] == 8 && fromPiece == -1) {
				System.err.println("Black pawn promoted to queen!");
				board[randomMove[2]][randomMove[3]] = -9; // promotion to queen
			} else {
				board[randomMove[2]][randomMove[3]] = fromPiece;
			}

		printCurrentCheckBoardPosition();
		whiteTurn = !whiteTurn;
	}
	
	
	private int[] possibleMoves(boolean white, String kingPosition, ArrayList<Object> possibleMoves, ArrayList<Object> kingThreads, ArrayList<Object> removalMoves, int board[][]) {
		
		
		int numberOfMoves;
		String piece;
		int [] randomMove;
		int fromPiece;
		int toPiece;
		
		for (int i = 0; i < possibleMoves.size(); ++i) {

			int candidateMove[] = (int[]) possibleMoves.get(i);
			int i1 = candidateMove[0];
			int j1 = candidateMove[1];
			int i2 = candidateMove[2];
			int j2 = candidateMove[3];
						
					
			fromPiece = board[i1][j1];
			toPiece = board[i2][j2];
			// test the candidate move
			board[i2][j2] = board[i1][j1]; // DO THE MOVE, O-O & O--O & el pase TODO
			board[i1][j1] = 0; // original place will always be empty after the move
						
			
			if (white) {
				listWhiteOpenChessesAfterCandidateMove();
			} else {
				listBlackOpenChessesAfterCandidateMove();	
			}
			
			if (kingThreads.contains(kingPosition)) {	
				removalMoves.add(candidateMove);
			}			
			board[i1][j1] = fromPiece;	// reverse the actual move; it was just to check the candidates!
			board[i2][j2] = toPiece;
		}
		
		
		possibleMoves.removeAll(removalMoves);
		numberOfMoves = possibleMoves.size();

		for (int i = 0; i < numberOfMoves; ++i) {
			int pos[] = (int[]) possibleMoves.toArray()[i];
			
			/*int pos[] = (int[]) possibleMovesBlack.toArray()[i];
			piece = pieceConverter(board[pos[0]][pos[1]], 0, 0);*/
			
			//System.out.println("value="pos[0]][pos[1]]);
			piece = pieceConverter(board[pos[0]][pos[1]], 0, 0);
			System.out.print((" " + (i + 1)) + ")" + piece + pos[0] + pos[1] + pos[2] + "" + pos[3] + " ");
			
			if ((i + 1) % 10 == 0) {
				System.out.println(); // just a new line for clarity
			}
		}

		int randomMoveNumber = rand.nextInt(numberOfMoves);
		randomMove = (int[]) possibleMoves.toArray()[randomMoveNumber];

		return randomMove;
	}

	// check all possible moves
	private boolean bishop(int i, int j) {

		int r = 0;
		int c = 0;

		while (isBasicMovePossible(i, j, ++r + i, ++c + j)) {
			addPossibleMovesAndThreads(i, j, i + r, j + c);

			if (board[i + r][j + c] != 0) { // EAT!
				break; // allow only one eat
			}
		}
		r = 0;
		c = 0;

		while (isBasicMovePossible(i, j, --r + i, ++c + j)) {
			addPossibleMovesAndThreads(i, j, i + r, j + c);
			if (board[i + r][j + c] != 0) { // EAT!
				break;
			}
		}
		r = 0;
		c = 0;

		while (isBasicMovePossible(i, j, --r + i, --c + j)) {
			addPossibleMovesAndThreads(i, j, i + r, j + c);
			if (board[i + r][j + c] != 0) { // EAT!
				break; // allow only one possible eat // TODO, check B-eat
						// problem
			}
		}
		r = 0;
		c = 0;

		while (isBasicMovePossible(i, j, ++r + i, --c + j)) {
			addPossibleMovesAndThreads(i, j, i + r, j + c);
			if (board[i + r][j + c] != 0) { // EAT!
				break;
			}
		}
		return false;
	}

	/**
	 *
	 * @param fromRow
	 * @param fromCol
	 * @param toRow
	 * @param toCol
	 * @return
	 */
	private boolean isBasicMovePossible(int fromRow, int fromCol, int toRow,
			int toCol) {


		if (DEBUG)
			System.err.println("***-KING-fromRow = " + fromRow + "fromCol=" + fromCol
					+ " toRow = " + toRow + " toCol= " + toCol);

		String piece = pieceConverter(board[fromRow][fromCol], fromCol, fromRow);

		String fromMove = "" + fromRow + fromCol;
		String toMove = "" + toRow + toCol;

		if (toRow > 8 || toRow < 1) {
			if (DEBUG)
				System.err.println("checking, toRow: " + toRow);
			return false; // check the board borders
		} else if (toCol > 8 || toCol < 1) {
			if (DEBUG)
				System.err.println("checking, toCol: " + toCol);
			return false;
		} else if (board[fromRow][fromCol] < 0 && board[toRow][toCol] < 0) {
			if (DEBUG)
				System.err.println("checkingA: " + piece);
			return false; // own piece blocks black

		} else if (board[fromRow][fromCol] > 0 && board[toRow][toCol] > 0) {

			if (DEBUG)
				System.err.println("checkingB: " + piece);
			return false; // own piece blocks white
		} else if (whiteTurn && board[fromRow][fromCol] == 6) { // white king
			
			if (blackThreadsAgainstWhiteKing.contains(fromMove)) {
				if (DEBUG) System.err.println("CHECK for WHITE");
				whiteChecked = true;
			}
			if (blackThreadsAgainstWhiteKing.contains(toMove)) {
				return false;
			}
			
		} else if (!whiteTurn && board[fromRow][fromCol] == -6) { // black king 
			
			// check condition! (shakki)
						if (whiteThreadsAgainstBlackKing.contains(fromMove)) {
							if (DEBUG) System.err.println("CHECK for black");
							blackChecked = true;
						}
						if (whiteThreadsAgainstBlackKing.contains(toMove)) {
							if (DEBUG) System.err.println("SHAKKI for black, not possible: " + toMove);
							return false;
						}
						
		
		}
		
		return true;
	}


	/**
	 *
	 * @param fromRow
	 * @param fromCol
	 * @param toRow
	 * @param toCol
	 */
	private void addPossibleMovesAndThreads(int fromRow, int fromCol, int toRow, int toCol) {

		String piece = pieceConverter(board[fromRow][fromCol], fromCol, fromRow);
		if (DEBUG)			System.err.println("checking: " + piece + "fromRow="+fromRow + " fromCol=" +fromCol);

		int move[] = new int[4];
		move[0] = fromRow;
		move[1] = fromCol;
		move[2] = toRow;
		move[3] = toCol;

		if (board[fromRow][fromCol] > 0) { // white piece
			
			if (whiteTurn && !afterWhiteMove) possibleMovesWhite.add(move); // adding a possible move

			if (afterWhiteMove) {  // white has just moved, so add all the current threads against black king
				whiteThreadsAgainstBlackKing.add("" + toRow + "" + toCol);
				 // TODO check threat (special case) for BLACK KING
				if (board[toRow][toCol] == -6) { // against black king!

					if (fromRow == toRow && fromCol < toCol) {
						whiteThreadsAgainstBlackKing.add("" + toRow + "" + ++toCol); // check; extend the threat!
					} else if (fromRow == toRow && fromCol > toCol) {
						whiteThreadsAgainstBlackKing.add("" + toRow + "" + --toCol); // check; extend the threat!
					} else if (fromCol == toCol && fromRow > toRow) {
						whiteThreadsAgainstBlackKing.add("" + --toRow + "" + toCol); // check; extend the threat!
					} else if (fromCol == toCol && fromRow < toRow) {
						whiteThreadsAgainstBlackKing.add("" + ++toRow + "" + toCol); // check; extend the threat!
					}

					if (board[fromRow][fromCol] == 4 || board[fromRow][fromCol] == 9) { // bishop or queen threats
						if (fromRow > toRow && fromCol < toCol) {
							whiteThreadsAgainstBlackKing.add("" + toRow-- + "" + toCol++); // check; extend the threat!
						} else if (fromRow < toRow && fromCol < toCol) {
							whiteThreadsAgainstBlackKing.add("" + toRow++ + "" + toCol++); // check; extend the threat!
						} else if (fromRow < toRow && fromCol > toCol) {
							whiteThreadsAgainstBlackKing.add("" + toRow++ + "" + toCol--); // check; extend the threat!
						} else if (fromRow > toRow && fromCol > toCol) {
							whiteThreadsAgainstBlackKing.add("" + toRow-- + "" + toCol--); // check; extend the threat!
						}
					}
				}
			}			
			
		} else if (board[fromRow][fromCol] < 0) { // black piece

			
			if (!whiteTurn && !afterBlackMove) possibleMovesBlack.add(move);

			if (afterBlackMove || openCheckAgainstWhite) { // black or white has just moved, so add all the current threads against white king
				blackThreadsAgainstWhiteKing.add("" + toRow + "" + toCol);
				if (DEBUG) System.err.println("adding new thread: = " + toRow + "" + toCol);
			 // TODO check threat (special case) for white KING

				if (board[toRow][toCol] == 6) { // against WHITE king
					if (fromRow == toRow && fromCol < toCol) {
						blackThreadsAgainstWhiteKing.add(Integer.toString(toRow) + Integer.toString(++toCol)); // check; extend the threat!
					} else if (fromRow == toRow && fromCol > toCol) {
						blackThreadsAgainstWhiteKing.add("" + toRow + "" + --toCol); // check; extend the threat!
					} else if (fromCol == toCol && fromRow > toRow) {
						blackThreadsAgainstWhiteKing.add("" + --toRow + "" + toCol); // check; extend the threat!
					} else if (fromCol == toCol && fromRow < toRow) {
						blackThreadsAgainstWhiteKing.add("" + ++toRow + "" + toCol); // check; extend the threat!
					}

					if (board[fromRow][fromCol] == -4 || board[fromRow][fromCol] == -9) { // bishop or queen threats

						if (fromRow > toRow && fromCol < toCol) {
							blackThreadsAgainstWhiteKing.add("" + toRow-- + "" + toCol++); // check; extend the threat!
						} else if (fromRow < toRow && fromCol < toCol) {
							blackThreadsAgainstWhiteKing.add("" + toRow++ + "" + toCol++); // check; extend the threat!
						} else if (fromRow < toRow && fromCol > toCol) {
							blackThreadsAgainstWhiteKing.add("" + toRow++ + "" + toCol--); // check; extend the threat!
						} else if (fromRow > toRow && fromCol > toCol) {
							blackThreadsAgainstWhiteKing.add("" + toRow-- + "" + toCol--); // check; extend the threat!
						}
					}
				}
			}
		} // eof black piece
	}

	/**
	 *
	 * @param fromRow
	 * @param fromCol
	 * @param toRow
	 * @param toCol
	 * @return
	 */
	private boolean kingMove(int fromRow, int fromCol, int toRow, int toCol) {

		boolean possible = isBasicMovePossible(fromRow, fromCol, toRow, toCol);


		if (possible) {
			addPossibleMovesAndThreads(fromRow, fromCol, toRow, toCol);
		} else return false;

		return true;
	}

	/**
	 *
	 * @param fromRow
	 * @param fromCol
	 * @param toRow
	 * @param toCol
	 * @return
	 */
	private boolean knightMove(int fromRow, int fromCol, int toRow, int toCol) {

		boolean possible = isBasicMovePossible(fromRow, fromCol, toRow, toCol);
		//System.out.println("knight possible = " + possible);
		if (possible) {

			addPossibleMovesAndThreads(fromRow, fromCol, toRow, toCol);
		} else return false;

		return true;
	}

	/**
	 *
	 * @param fromRow
	 * @param fromCol
	 * @param toRow
	 * @param toCol
	 * @return
	 */
	private synchronized boolean pawnMove(int fromRow, int fromCol, int toRow,
			int toCol) {
		int firstDif = toRow - fromRow;
		int secondDif = toCol - fromCol;
		boolean accepted = false;
		boolean possible = isBasicMovePossible(fromRow, fromCol, toRow, toCol);


		if (possible) {
			addPossibleMovesAndThreads(fromRow, fromCol, toRow, toCol);
		}

		// add collision detection! END MOVE IF COLLISION

		// TODO handle ohestalyönti el passe, toRow == 9 ?!
		if (whiteTurn) { // WHITE
			if (firstDif == -1 && secondDif == 0) {
				//FIXME
				return true;
			}
			if (fromRow == 7 && firstDif == -2 && secondDif == 0) {
				// check collision
				if (board[6][fromCol] == 0) {
					return true;
				} else {
					if (DEBUG)
						System.err
								.println("COLLISION: White pawn two up attempt!");
					return false;
				}

			}
			if (firstDif == -1 && Math.abs(secondDif) == 1
					&& (board[toRow][toCol] < 0)) {
				System.err.println("White pawn eats!");
				return true;
			}

		} else { // BLACK

			if (firstDif == 1 && secondDif == 0) {
				//FIXME
				return true;
			}
			if (fromRow == 2 && firstDif == 2 && secondDif == 0) {
				// check collision
				if (board[3][fromCol] == 0) {
					return true;
				} else {
					System.err.println("COLLISION: Black pawn two up attempt!");
					return false;
				}
			}
			if (firstDif == 1 && Math.abs(secondDif) == 1
					&& (board[toRow][toCol] > 0)) {
				return true;
			}
		}
		return accepted;
	}

  //COLUMN , ROW order!!!
	private String pieceConverter(int value, int column, int row) {

		if (value == -1)
			return "♟ "; // Pawn
		if (value == -3)
			return "♞ "; // kNight
		if (value == -4)
			return "♝ "; // Bishop
		if (value == -5)
			return "♜ "; // Rook
		if (value == -6)
			return "♚ "; // King
		if (value == -9)
			return "♛ "; // Queen

		if (value == 1)
			return "♙ "; // Pawn
		if (value == 3)
			return "♘ "; // kNight
		if (value == 4)
			return "♗ "; // Bishop
		if (value == 5)
			return "♖ "; // Rook
		if (value == 6)
			return "♔ "; // King
		if (value == 9)
			return "♕ "; // Queen◽

		if (column % 2 == 0 && row % 2 != 0) {

			return "\u2B1B "; //black square
		}
		if (column % 2 != 0 && row % 2 == 0) {

			return "\u2B1B "; // black square
		}
		return "\u2B1C "; // white square

		// return "" + value;
	}
	
	
	// white or black has tested the move (candidate), now check whether that caused a thread to its king
	private boolean listWhiteOpenChessesAfterCandidateMove() {

		blackThreadsAgainstWhiteKing.clear();

		for (int i = 1; i < 9; ++i) {
			for (int j = 1; j < 9; ++j) {
				int pieceValue = board[i][j]; // identify the piece (black/white
												// & type)
				switch (pieceValue) {
				case 9: // white queen
					queen(i, j);
					break;
				case 5:
					rook(i, j);
					break;
				case 4:
					bishop(i, j);
					break;
				default: break;
				}
			}
		}
		return false;
	}


	private boolean listBlackOpenChessesAfterCandidateMove() {

		whiteThreadsAgainstBlackKing.clear();

		for (int i = 1; i < 9; ++i) {
			for (int j = 1; j < 9; ++j) {
				int pieceValue = board[i][j]; // identify the piece (black/white
												// & type)
				switch (pieceValue) {
				case 9: // white queen
					queen(i, j);
					break;
				case 5:
					rook(i, j);
					break;
				case 4:
					bishop(i, j);
					break;
				default: break;
				} 
				
			}
		}
		return false;
	}


	/**
	 *
	 * @param pieceValue
	 * @param fromRow
	 * @param fromCol
	 */
	private synchronized void checkAllPossibleMoves(int pieceValue,
			int fromRow, int fromCol) {


		int value = 0;
		if (pieceValue != - 1 ) {
			value = Math.abs(pieceValue);
		}

		switch (value) {
		case 9:
			queen(fromRow, fromCol);
			break;
		case 6:
			// special cases: EAT (OK), CHESS (!OK), OWN (!OK)

			kingMove(fromRow, fromCol, fromRow + 1, fromCol);
			kingMove(fromRow, fromCol, fromRow + 1, fromCol + 1);
			kingMove(fromRow, fromCol, fromRow, fromCol + 1);
			kingMove(fromRow, fromCol, fromRow - 1, fromCol);
			kingMove(fromRow, fromCol, fromRow - 1, fromCol + 1);
			kingMove(fromRow, fromCol, fromRow - 1, fromCol - 1);
			kingMove(fromRow, fromCol, fromRow, fromCol - 1);
			kingMove(fromRow, fromCol, fromRow + 1, fromCol - 1);
			break;
		case 5:
			rook(fromRow, fromCol);
			break;
		case 4:
			bishop(fromRow, fromCol);
			break;
		case 3:

			knightMove(fromRow, fromCol, fromRow + 1, fromCol + 2);
			knightMove(fromRow, fromCol, fromRow - 1, fromCol + 2);

			knightMove(fromRow, fromCol, fromRow + 1, fromCol - 2);
			knightMove(fromRow, fromCol, fromRow - 1, fromCol - 2);

			knightMove(fromRow, fromCol, fromRow + 2, fromCol + 1);
			knightMove(fromRow, fromCol, fromRow - 2, fromCol + 1);

			knightMove(fromRow, fromCol, fromRow + 2, fromCol - 1);
			knightMove(fromRow, fromCol, fromRow - 2, fromCol - 1);
			break;
		case 1:
			if (fromRow == 7) {
				pawnMove(fromRow, fromCol, fromRow - 1, fromCol); // move ONE
				pawnMove(fromRow, fromCol, fromRow - 2, fromCol); // move TWO
				// TODO add ohestalyönti case
			} else
				pawnMove(fromRow, fromCol, fromRow - 1, fromCol);
			break;
		case -1: // black pawn
			if (fromRow == 2) {
				pawnMove(fromRow, fromCol, fromRow + 1, fromCol); // move ONE
				pawnMove(fromRow, fromCol, fromRow + 2, fromCol); // move two
			} else
				pawnMove(fromRow, fromCol, fromRow + 1, fromCol);
			break;
		 default: break;
	
		}
	}

	// reinforced learning, heuristical game function


	private void printCurrentCheckBoardPosition() {
		System.out.println("");

		for (int c = 1; c < 9; ++c) {
			board[0][c] = c;
		}

		for (int r = 1; r < 9; ++r) {
			board[r][0] = r;
		}
		//System.out.print("\u200B"); // zero-width space
		for (int r = 0; r < 9; ++r) {
			System.out.print("");
			for (int c = 0; c < 9; ++c) {
				if (c == 1)
					System.out.print("");

				if (r == 0 && c == 0) {
					System.out.print(" ");
				} else if (r == 0 || c == 0) {
					if (c % 3 == 0) {
						System.out.print(" "+board[r][c]);

					} else {
						System.out.print(" " + board[r][c]);
					}

				} else {
					System.out.print(pieceConverter(board[r][c], c, r));
				}
			}
			System.out.println();
		}
	}

	/**
	 *
	 * @param fromRow
	 * @param fromCol
	 * @return
	 */
	private boolean queen(int fromRow, int fromCol) {

		boolean rookMove = rook(fromRow, fromCol);
		boolean bishopMove = bishop(fromRow, fromCol);
		return rookMove || bishopMove;
	}

	// check all possible moves
	private boolean rook(int i, int j) {

		int r = 0;
		int c = 0;

		while (isBasicMovePossible(i, j, ++r + i, j)) {
			addPossibleMovesAndThreads(i, j, i + r, j);
			if (board[i + r][j] != 0) { // EAT!
				if (DEBUG)
					System.err.print(" R-EAT:(" + i + "" + j + ", " + i + r
							+ "" + j + c + ")");
				break;
			}

		}
		r = 0;
		c = 0;

		while (isBasicMovePossible(i, j, --r + i, j)) {
			addPossibleMovesAndThreads(i, j, i + r, j);
			if (board[i + r][j] != 0) { // EAT!
				if (DEBUG)
					System.err.print(" R-EAT:(" + i + "" + j + ", " + i + r
							+ "" + j + c + ")");
				break;
			}
		}
		r = 0;
		c = 0;

		while (isBasicMovePossible(i, j, i, ++c + j)) {
			addPossibleMovesAndThreads(i, j, i, j + c);
			if (board[i][j + c] != 0) { // EAT!
				if (DEBUG)
					System.err.print(" R-EAT:(" + i + "" + j + ", " + i + r
							+ "" + j + c + ")");
				break;
			}
		}
		r = 0;
		c = 0;

		while (isBasicMovePossible(i, j, i, --c + j)) {
			addPossibleMovesAndThreads(i, j, i, j + c);
			if (board[i][j + c] != 0) { // EAT!
				if (DEBUG)
					System.err.print(" R-EAT:(" + i + "" + j + ", " + i + r
							+ "" + j + c + ")");
				break;
			}
		}
		return false;
	}
}