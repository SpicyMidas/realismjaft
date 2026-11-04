package dev.agentbuddy.brain.reply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReplyFilterTest {
    @Test
    void stripsFillerOpenersAndPraise() {
        assertEquals("Iron is to the north.", ReplyFilter.clean("Sure! Iron is to the north."));
        assertEquals("That is stone.", ReplyFilter.clean("Great question! That is stone."));
        assertEquals("You're in the overworld.", ReplyFilter.clean("Absolutely, you're in the overworld."));
        assertEquals("Hi.", ReplyFilter.clean("Hi."));
    }

    @Test
    void stripsApologies() {
        assertEquals("I can't do that.", ReplyFilter.clean("I'm sorry, I can't do that."));
        assertEquals("I can't do that.", ReplyFilter.clean("I can't do that, sorry."));
    }

    @Test
    void keepsFirstSentenceOnly() {
        assertEquals("I help with the game.", ReplyFilter.clean("I help with the game. I can find ores, fetch items and more!"));
        assertEquals("Hello there.", ReplyFilter.clean("Hello there.\nI am the Agent."));
    }

    @Test
    void capsAtFifteenWords() {
        String out = ReplyFilter.clean("one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen seventeen");
        assertEquals(15, ReplyFilter.wordCount(out));
        assertTrue(out.endsWith("."));
    }

    @Test
    void removesEmojiMarkupAndThinking() {
        assertEquals("Hi.", ReplyFilter.clean("Hi! 😀").replace("!", "."));
        assertEquals("Stone.", ReplyFilter.clean("**Stone.**"));
        assertEquals("Stone.", ReplyFilter.clean("<think>hmm</think>Stone."));
    }

    @Test
    void emptyAfterFilteringFallsBack() {
        assertEquals(Templates.DIDNT_UNDERSTAND, ReplyFilter.clean("Sure!"));
        assertEquals(Templates.DIDNT_UNDERSTAND, ReplyFilter.clean("   "));
        assertEquals(Templates.DIDNT_UNDERSTAND, ReplyFilter.clean(null));
    }

    @Test
    void addsTerminalPunctuation() {
        assertEquals("No, you're in the overworld.", ReplyFilter.clean("No, you're in the overworld"));
    }
}
