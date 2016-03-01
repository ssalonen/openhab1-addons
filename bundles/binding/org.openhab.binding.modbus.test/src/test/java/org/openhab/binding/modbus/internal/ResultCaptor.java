package org.openhab.binding.modbus.internal;

import java.util.ArrayList;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class ResultCaptor<T> implements Answer<T> {

	private ArrayList<T> results = new ArrayList<T>();

	public ArrayList<T> getAllReturnValues() {
		return results;
	}

	@SuppressWarnings("unchecked")
	@Override
	public T answer(InvocationOnMock invocationOnMock) throws Throwable {
		T result = (T) invocationOnMock.callRealMethod();
		synchronized (this.results) {
			results.add(result);
		}
		return result;
	}
}