package br.com.rads.jankenpon;

public class Player {

	private String name;
	private String move;
	
	public Player(String name) {
		super();
		this.name = name;
		this.move = "";
	}
	
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getMove() {
		return move;
	}
	public void setMove(String move) {
		this.move = move;
	}
	
}
