package com.card.Yugioh.model;

public enum RaceEnum {
    AQUA("물족"),
    BEAST("야수족"),
    BEAST_WARRIOR("야수전사족"),
    CYBERSE("사이버스족"),
    DINOSAUR("공룡족"),
    DIVINE_BEAST("환신야수족"),
    DRAGON("드래곤족"),
    FAIRY("천사족"),
    FIEND("악마족"),
    FISH("어류족"),
    ILLUSION("환상마족"),
    INSECT("곤충족"),
    MACHINE("기계족"),
    PLANT("식물족"),
    PSYCHIC("사이킥족"),
    PYRO("화염족"),
    REPTILE("파충류족"),
    ROCK("암석족"),
    SEA_SERPENT("해룡족"),
    SPELLCASTER("마법사족"),
    THUNDER("번개족"),
    WARRIOR("전사족"),
    WINGED_BEAST("비행야수족"),
    WYRM("환룡족"),
    ZOMBIE("언데드족"),
    // Spell/Trap types
    NORMAL("일반"),
    CONTINUOUS("지속"),
    EQUIP("장착"),
    FIELD("필드"),
    QUICK_PLAY("속공"),
    RITUAL("의식"),
    COUNTER("카운터");

    private final String race;

    RaceEnum(String race) {
        this.race = race;
    }

    public String getRace() {
        return race;
    }
    
    public static RaceEnum fromEnglishName(String enRace) {
        try {
            return RaceEnum.valueOf(enRace.toUpperCase().replaceAll("[ -]", "_"));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown race: " + enRace);
        }
    }
}
