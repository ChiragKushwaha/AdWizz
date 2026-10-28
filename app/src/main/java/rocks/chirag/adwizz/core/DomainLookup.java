package rocks.chirag.adwizz.core;

/** Something that can say whether a domain is listed: the on-disk {@link DomainIndex} or an in-memory {@link DomainSet}. */
public interface DomainLookup {
    /** True if s.subSequence(start, s.length()) is listed, without creating that substring. */
    boolean contains(CharSequence s, int start);

    int size();
}
