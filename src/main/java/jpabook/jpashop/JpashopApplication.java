package jpabook.jpashop;

import com.fasterxml.jackson.datatype.hibernate5.Hibernate5Module;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class JpashopApplication {

	public static void main(String[] args) {
		SpringApplication.run(JpashopApplication.class, args);
	}

	//굳이 이걸 사용할 필요 없다, 그냥 엔티티를 노출 안하면 되는
	//OrderSimpleApiControlle의 지연로딩 막기 위해
	//기본 설정 자체가 프록시면 데이터 안뿌림 lazy강제초기화하면 뿌림
	@Bean
	Hibernate5Module hibernate5Module() {
		Hibernate5Module hibernate5Module = new Hibernate5Module();
		//강제 지연 로딩 설정, json 생성하는 시점에 lazy loading해버림
		hibernate5Module.configure(Hibernate5Module.Feature.FORCE_LAZY_LOADING, true);
		return hibernate5Module;
	}
}
