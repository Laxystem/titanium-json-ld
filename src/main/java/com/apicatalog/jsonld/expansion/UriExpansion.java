package com.apicatalog.jsonld.expansion;

import java.util.Map;

import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.json.JsonValue.ValueType;

import com.apicatalog.jsonld.JsonLdError;
import com.apicatalog.jsonld.context.ActiveContext;
import com.apicatalog.jsonld.context.TermDefinition;
import com.apicatalog.jsonld.context.TermDefinitionCreator;
import com.apicatalog.jsonld.grammar.CompactUri;
import com.apicatalog.jsonld.grammar.Keywords;
import com.apicatalog.jsonld.utils.UriUtils;
/**
 * 
 * 
 * @see <a href="https://www.w3.org/TR/json-ld11-api/#algorithm-4">IRI Expansion</a>
 *
 */
public final class UriExpansion {

	// mandatory
	private ActiveContext activeContext;
	private String value;
	
	// optional
	private boolean documentRelative;
	private boolean vocab;
	
	private JsonObject localContext;
	private Map<String, Boolean> defined;
		
	private UriExpansion(final ActiveContext activeContext, final String value) {
		this.activeContext = activeContext;
		this.value = value;
		
		// default values
		this.documentRelative = false;
		this.vocab = false;
		this.localContext = null;
		this.defined = null;
	}
	
	public static final UriExpansion with(final ActiveContext activeContext, final String value) {
		return new UriExpansion(activeContext, value);
	}

	public UriExpansion documentRelative(boolean value) {
		this.documentRelative = value;
		return this;
	}

	public UriExpansion vocab(boolean value) {
		this.vocab = value;
		return this;
	}

	public UriExpansion localContext(JsonObject value) {
		this.localContext = value;
		return this;
	}
	
	public UriExpansion defined(Map<String, Boolean> value) {
		this.defined = value;
		return this;
	}
	
	public String compute() throws JsonLdError {

		// 1. If value is a keyword or null, return value as is.
		if (value == null || Keywords.contains(value)) {
			return value;
		}
		
		// 2. If value has the form of a keyword (i.e., it matches the ABNF rule "@"1*ALPHA from [RFC5234]),
		//	  a processor SHOULD generate a warning and return null.
		if (Keywords.hasForm(value)) {
			//TODO varning
			return null;
		}
	
		/*
		 *  3. If local context is not null, it contains an entry with a key that equals value, 
		 *     and the value of the entry for value in defined is not true, invoke the Create Term Definition algorithm, 
		 *     passing active context, local context, value as term, and defined. 
		 *     This will ensure that a term definition is created for value in active context during Context Processing 
		 */
		if (localContext != null && localContext.containsKey(value)) {
			
			JsonValue entryValue = localContext.get(value);
			
			if (ValueType.STRING.equals(entryValue.getValueType())) {

				String entryValueString = ((JsonString)entryValue).getString();
				
				if (!defined.containsKey(entryValueString) || Boolean.FALSE.equals(defined.get(entryValueString))) {

					TermDefinitionCreator.with(activeContext, localContext, value, defined).create();		
				}
			}
		}

		// 4. if active context has a term definition for value, 
		//	  and the associated IRI mapping is a keyword, return that keyword.
		if (activeContext.containsTerm(value)) {
			
			TermDefinition termDefinition = activeContext.getTerm(value);

			// 5. If vocab is true and the active context has a term definition for value, return the associated IRI mapping
			if (Keywords.contains(termDefinition.getUriMapping()) || vocab) {
				return termDefinition.getUriMapping();
			}
		}
		
		// 6. If value contains a colon (:) anywhere after the first character, it is either an IRI, 
		//    a compact IRI, or a blank node identifier
		if (value.indexOf(':', 1) != -1) {
			
			// 6.1. Split value into a prefix and suffix at the first occurrence of a colon (:).
			String[] split = value.split(":", 2);
			
			// 6.2. If prefix is underscore (_) or suffix begins with double-forward-slash (//), 
			//		return value as it is already an IRI or a blank node identifier.
			if ("_".equals(split[0]) || split[1].startsWith("//")) {
				return value;
			}
			
			// 6.3.
			if (localContext != null && localContext.containsKey(split[0])) {
				
				JsonValue prefixValue = localContext.get(split[0]);
				
				if (ValueType.STRING.equals(prefixValue.getValueType())) {

					String prefixValueString = ((JsonString)prefixValue).getString();
					
					if (!defined.containsKey(prefixValueString) || Boolean.FALSE.equals(defined.get(prefixValueString))) {

						TermDefinitionCreator.with(activeContext, localContext, split[0], defined).create();		
					}
				}								
			}
			
			// 6.4.
			if (activeContext.containsTerm(split[0])) {
				
				TermDefinition prefixDefinition = activeContext.getTerm(split[0]);
				
				if (prefixDefinition != null
					&& prefixDefinition.getUriMapping() != null
					&& prefixDefinition.isPrefix()
						) {
					
					value = prefixDefinition.getUriMapping().concat(split[1]);
				}
			}

			// 6.5
			if (CompactUri.create(value) != null && UriUtils.isURI(value)) {
				return value;
			}
		}
		
		// 7. If vocab is true, and active context has a vocabulary mapping, 
		//    return the result of concatenating the vocabulary mapping with value.
		if (vocab && activeContext.getVocabularyMapping() != null) {
			return activeContext.getVocabularyMapping().toString().concat(value);
			
		// 8.
		} else if (documentRelative) {
			value = UriUtils.resolve(activeContext.getBaseUri(), value);
		}
		
		// 9.
		return value;
	}
	
	
}
