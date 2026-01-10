"""Command line tool for generating and shuffling a standard deck of playing cards."""
from __future__ import annotations

import argparse
import random
from dataclasses import dataclass
from typing import Iterable, List, Sequence

SUITS = ["Clubs", "Diamonds", "Hearts", "Spades"]
RANKS = [
    "Ace",
    "2",
    "3",
    "4",
    "5",
    "6",
    "7",
    "8",
    "9",
    "10",
    "Jack",
    "Queen",
    "King",
]


@dataclass(frozen=True)
class Card:
    """Representation of a single playing card."""

    rank: str
    suit: str

    def __str__(self) -> str:  # pragma: no cover - trivial representation
        return f"{self.rank} of {self.suit}"


class Deck:
    """A deck of playing cards that can be shuffled."""

    def __init__(self, cards: Sequence[Card]):
        self._cards: List[Card] = list(cards)

    @classmethod
    def standard(cls, decks: int = 1) -> "Deck":
        """Create a standard 52-card deck, optionally repeating ``decks`` times."""

        cards = [Card(rank, suit) for rank in RANKS for suit in SUITS] * decks
        return cls(cards)

    def shuffle(self, *, seed: int | None = None) -> None:
        """Randomize the order of the cards in-place."""

        random.Random(seed).shuffle(self._cards)

    def __iter__(self) -> Iterable[Card]:
        return iter(self._cards)

    def __len__(self) -> int:
        return len(self._cards)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "-n",
        "--decks",
        type=int,
        default=1,
        help="Number of standard decks to include (default: 1)",
    )
    parser.add_argument(
        "--seed",
        type=int,
        default=None,
        help="Optional random seed to produce reproducible shuffles.",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if args.decks < 1:
        raise SystemExit("--decks must be at least 1")

    deck = Deck.standard(args.decks)
    deck.shuffle(seed=args.seed)

    for card in deck:
        print(card)


if __name__ == "__main__":
    main()
