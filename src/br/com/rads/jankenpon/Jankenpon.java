package br.com.rads.jankenpon;

import android.util.Log;

public abstract class Jankenpon {

	public static final String ROCK = "Rock";
	public static final String PAPER = "Paper";
	public static final String SCISSOR = "Scissor";

	public static Player fight(Player player, Player enemy) {

		Log.d("PLAYER", "Used" + player.getMove());
		Log.d("ENEMY", "Used" + enemy.getMove());
		
		String playerMove = player.getMove();
		String enemyMove = enemy.getMove();

		if (playerMove.equalsIgnoreCase(enemyMove))
			return null;

		if ((playerMove.equals(ROCK) && enemyMove.equals(SCISSOR))
				|| (playerMove.equals(SCISSOR) && enemyMove.equals(PAPER))
				|| (playerMove.equals(PAPER) && enemyMove.equals(ROCK))) {
			return player;
		}

		return enemy;
	}
}
