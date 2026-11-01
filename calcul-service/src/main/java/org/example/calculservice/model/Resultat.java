package org.example.calculservice.model;



public class Resultat {

    private String operation;
    private int a;
    private int b;
    private double resultat;

    // Constructeur sans argument : obligatoire pour la conversion JSON
    public Resultat() {
    }

    public Resultat(String operation, int a, int b, double resultat) {
        this.operation = operation;
        this.a = a;
        this.b = b;
        this.resultat = resultat;
    }

    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }

    public int getA() { return a; }
    public void setA(int a) { this.a = a; }

    public int getB() { return b; }
    public void setB(int b) { this.b = b; }

    public double getResultat() { return resultat; }
    public void setResultat(double resultat) { this.resultat = resultat; }
}
