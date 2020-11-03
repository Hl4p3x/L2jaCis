package net.sf.l2j.gameserver.data.xml;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import net.sf.l2j.commons.data.xml.IXmlReader;
import net.sf.l2j.commons.util.StatsSet;

import net.sf.l2j.gameserver.model.holder.NewbieBuffHolder;

import org.w3c.dom.Document;

/**
 * This class loads and store {@link NewbieBuffHolder} into a {@link List}.
 */
public class NewbieBuffData implements IXmlReader
{
	private final List<NewbieBuffHolder> _buffs = new ArrayList<>();
	
	private int _magicLowestLevel = 100;
	private int _physicLowestLevel = 100;
	
	protected NewbieBuffData()
	{
		load();
	}
	
	@Override
	public void load()
	{
		parseFile("./data/xml/newbieBuffs.xml");
		LOGGER.info("Loaded {} newbie buffs.", _buffs.size());
	}
	
	@Override
	public void parseDocument(Document doc, Path path)
	{
		forEach(doc, "list", listNode -> forEach(listNode, "buff", buffNode ->
		{
			final StatsSet set = parseAttributes(buffNode);
			final int lowerLevel = set.getInteger("lowerLevel");
			if (set.getBool("isMagicClass"))
			{
				if (lowerLevel < _magicLowestLevel)
					_magicLowestLevel = lowerLevel;
			}
			else
			{
				if (lowerLevel < _physicLowestLevel)
					_physicLowestLevel = lowerLevel;
			}
			_buffs.add(new NewbieBuffHolder(set));
		}));
	}
	
	/**
	 * @param isAlikeMage : If true, return buffs list associated to mage-alike classes.
	 * @param level : Filter the list by the given level.
	 * @return The {@link List} of valid {@link NewbieBuffHolder}s for the given class type and level.
	 */
	public List<NewbieBuffHolder> getValidBuffs(boolean isAlikeMage, int level)
	{
		return _buffs.stream().filter(b -> b.isMagicClassBuff() == isAlikeMage && level >= b.getLowerLevel() && level <= b.getUpperLevel()).collect(Collectors.toList());
	}
	
	public int getLowestBuffLevel(boolean isMage)
	{
		return (isMage) ? _magicLowestLevel : _physicLowestLevel;
	}
	
	public static NewbieBuffData getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private static class SingletonHolder
	{
		protected static final NewbieBuffData INSTANCE = new NewbieBuffData();
	}
}