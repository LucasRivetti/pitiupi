package pitiupi.jokenpo.logica;

public enum Jogada {
    PEDRA,
    PAPEL,
    TESOURA;

    public boolean ganhaDe(Jogada outra) {
        return (this == PEDRA && outra == TESOURA)
                || (this == PAPEL && outra == PEDRA)
                || (this == TESOURA && outra == PAPEL);
    }
}
