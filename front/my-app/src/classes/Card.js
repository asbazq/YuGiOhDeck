class Card {
  constructor({ imageUrl, frameType, name, restrictionType }) {
    this.imageUrl = imageUrl;
    this.frameType = frameType;
    this.name = name;
    this.restrictionType = restrictionType;
  }
}

export default Card;