package com.xebialabs.restito.server;

import com.google.common.collect.Lists;
import com.xebialabs.restito.semantics.Call;
import com.xebialabs.restito.semantics.Stub;
import com.xebialabs.restito.support.behavior.Behavior;
import com.xebialabs.restito.support.log.CallsHelper;
import org.apache.mina.util.AvailablePortFinder;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class StubServer {

	public final static int DEFAULT_PORT = 6666;

	private List<Call> calls = Lists.newArrayList();
	private List<Stub> stubs = Lists.newArrayList();
	private HttpServer simpleServer;

	private Logger log = LoggerFactory.getLogger(StubServer.class);


	public StubServer(Stub... stubs) {
		this.stubs = new ArrayList<Stub>(Arrays.asList(stubs));
		simpleServer = HttpServer.createSimpleServer(".", AvailablePortFinder.getNextAvailable(DEFAULT_PORT));
	}

	public StubServer(Behavior behavior) {
		this(behavior.getStubs().toArray(new Stub[behavior.getStubs().size()]));
	}

	public StubServer addStub(Stub s) {
		this.stubs.add(s);
		return this;
	}

	public StubServer run() {
		simpleServer.getServerConfiguration().addHttpHandler(stubsToHandler(), "/");
		start();
		return this;
	}

	public void start() {
		try {
			simpleServer.start();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public StubServer stop() {
		simpleServer.stop();
		return this;
	}

	public int getPort() {
		return simpleServer.getListeners().iterator().next().getPort();
	}

	public List<Call> getCalls() {
		return calls;
	}

	public List<Stub> getStubs() {
		return stubs;
	}

	private HttpHandler stubsToHandler() {
		return new HttpHandler() {
			@Override
			public void service(Request request, Response response) throws Exception {

				Call call = Call.fromRequest(request);

				CallsHelper.logCall(call);

				boolean processed = false;

				for (Stub stub : Lists.reverse(stubs)) {
					if (!stub.isApplicable(call)) {
						continue;
					}

					stub.apply(response);
					processed = true;
					break;
				}

				if (!processed) {
					response.setStatus(HttpStatus.NOT_FOUND_404);
					log.warn("Request {} hasn't been covered by any of {} stubs.", request.getRequestURI(), stubs.size());
				}

				calls.add(call);
			}
		};
	}

}
