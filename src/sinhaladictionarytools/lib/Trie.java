/*******************************************************************************
 *     A java Trie data structure implementation based on
 *     "Algorithms in Java (3rd Edition) by Robert Sedgewick"
 *
 *     Copyright (C) 2010  AA   [Email|mailto:Godaddy.com@Live.com]
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program. If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/

package sinhaladictionarytools.lib;

/**
 * This class contains a data structure "Trie".<p>
 * @author AA
 * @version 2.2
 * @see <b><a href="mailto:Godaddy.com@Live.com">Email</a></b>
 * @see [Email|mailto:Godaddy.com@Live.com]
 * @see TrieNode
 */
public class Trie
{

    /**
     * ASCII numbers.
     */
    private static int alphabet = 256;

    /**
     * Root of the trie DS.
     */
    private TrieNode root = new TrieNode();

    /**
     * Size of the trie DS.
     */
    private int size = 0;

    /**
     * The number of times this Trie has been structurally modified. Structural
     * modifications are those that change the number of entries in the Trie.
     * @since version 2.2
     */
    private int modCount = 0;

    /**
     * Ignores the letter case.
     * @since version 2.1
     */
    private boolean ignoreCase = true;

    /**
     * Default constructor creates an empty trie.
     */
    public Trie() {
        root = new TrieNode();
        size = 0;
        modCount = 0;
    }

    /**
     * Default constructor creates an empty trie.
     *
     * @param maxCharCodePoint the maximum code point value of the intended alphabate
     */
    public Trie(int maxCharCodePoint) {        
        this.alphabet = maxCharCodePoint;
        root = new TrieNode();
        size = 0;
        modCount = 0;
    }

    /**
     * Appends the specified word to the end of this trie.
     * @param word - string to be appended to this trie.
     */
    public void add(String word) {
        if (!contains(word)) {
            root = add(root, ignoreCase ? word.toLowerCase().trim()
                    : word.trim(), 0);
            size++;
        } else {
            System.err.println("Word: \"" +word+ "\" has been entered before.");
        }
    }

    /**
     * @param tree - root of this trie.
     * @param word - string to be appended to this trie.
     * @param offset - word offset.
     * @see #add(String)
     */
    private TrieNode add(TrieNode tree, String word, int offset) {
        if (tree == null) {
            tree = new TrieNode();
        }
        if (word.length() == offset) {
            tree.endOfWord = true;
        } else {
            char c = word.charAt(offset);
            modCount++;
            tree.next[c] = add(tree.next[c], word, offset + 1);
        }
        return tree;
    }

    /**
     * Removes all of the words from this trie.
     * The trie will be empty after this call returns.
     */
    public void clear() {
        root = null;
        size = 0;
        modCount = 0;
    }

    /**
     * Returns {@code true} if this trie contains the specified word.
     * More formally, returns {@code true} if and only if this
     * trie contains at least one word {@code word} such that
     * <tt>(o==null ? word==null : o.equals(word))</tt>.
     * @param word - string whose presence in this trie is to be tested.
     * @return {@code true} if this trie contains the specified word.
     */
    public boolean contains(String word) {
        return word.isEmpty() ? false : contains(root,
                ignoreCase ? word.toLowerCase().trim() : word.trim(), 0);
    }

    /**
     * @param tree - root of this trie.
     * @param word - string in this trie to be tested.
     * @param offset - word offset.
     * @see #contains(String)
     */
    private boolean contains(TrieNode tree, String word, int offset) {
        if (tree == null) {
            return false;
        }
        if (word.length() == offset) {
            return tree.endOfWord;
        } else {
            char c = word.charAt(offset);
            return contains(tree.next[c], word, offset + 1);
        }
    }

  /**
     * Returns {@code true} if this trie contains the specified prefix.
     * More formally, returns {@code true} if and only if this
     * trie contains at least one prefix {@code p} such that
     * <tt>(o==null ? p==null : o.equals(p))</tt>.
     * @param prefix - string whose presence in this trie is to be tested.
     * @return {@code true} if this trie contains the specified prefix.
     */
    public boolean containsPrefix(String prefix) {
        return prefix.isEmpty() ? false : containsPrefix(root, ignoreCase ?
                prefix.toLowerCase().trim() : prefix.trim(), 0);
    }

    /**
     * @param tree - root of this trie.
     * @param prefix - string in this trie to be tested.
     * @param offset - prefix offset.
     * @see #containsPrefix(String)
     */
    private boolean containsPrefix(TrieNode tree, String prefix, int offset) {
        if (tree == null) {
            return false;
        }
        if (prefix.length() == offset) {
            return true;
        }
        else {
            char c = prefix.charAt(offset);

            return containsPrefix(tree.next[c], prefix, offset + 1);
        }
    }

    /**
     * Removes the specified word from this trie, if it is present.
     * If this trie does not contain the word, it is unchanged.
     * @param word - string to be removed from this trie, if present.
     */
    public void delete(String word) {

        if (contains(word)) {
            root = delete(root, ignoreCase ? word.toLowerCase().trim()
                    : word.trim(), 0);
            size--;
        } else {
            System.err.println("Can't find word: \"" + word + "\"");
        }
    }

    /**
     * @param tree - root of this trie.
     * @param word - string in this trie to be removed.
     * @param offset - word offset.
     * @see #delete(String)
     */
    private TrieNode delete(TrieNode tree, String word, int offset) {
        if (tree == null)
            return null;
        if (word.length() == offset) {
            tree.endOfWord = false;
        }
        else {
            char c = word.charAt(offset);
            modCount--;
            tree.next[c] = delete(tree.next[c], word, offset + 1);
        }
        if (tree.endOfWord) {
            return tree;
        }
        for (int i = 0; i < alphabet; i++)
            if (tree.next[i] != null)
                return tree;
        return null;
    }

    /**
     * Setting this to {@code true} will make this trie ignores the letters case.
     * @param ignoreCase - set to {@code true} to ignore case.
     */
    public void ignoreCase(boolean ignoreCase) {
        this.ignoreCase = ignoreCase;
    }

    /**
     * Returns the number of times this Trie has been structurally modified.
     * @return the number of times this Trie has been structurally modified.
     */
    public final int modCount() {
        return modCount;
    }

    /**
     * Prints the trie contents.
     */
    public void print() {
        if (root == null) {
            System.err.println("DS is empty '0' entries found.");
        } else {
            print(root, "");
        }
    }

    /**
     * @param tree - root of this trie.
     * @param prefix - the initial string.
     * @see #print()
     */
    private void print(TrieNode tree, String prefix) {
        if (tree == null)
            return;
        if (tree.endOfWord) {
            System.out.println(ignoreCase ?
                    prefix.toLowerCase().trim() : prefix.trim());
        }
        for (int i = 0; i < alphabet; i++)
            print(tree.next[i], prefix + (char) i);
    }

    /**
     * Returns the number of words in this trie.
     * @return the number of words in this trie.
     */
    public final int size() {
        return size;
    }

    /**
     * This class implement a node structure which is
     * used as the structural components of the trie.
     */
    private class TrieNode
    {
        /**
         * A 26 size array to hold the alphabet.
         */
        private TrieNode[] next = new TrieNode[alphabet];

        /**
         * A flag to indicate whether this node
         * represents an end of a word or not.
         */
        private boolean endOfWord = false;
    }    
}