package jpabook.jpashop.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import javax.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter @Setter
@NamedQueries( {
        @NamedQuery(name = "Member.findByUsername", query = "select m from Member m where m.username = :username"),
        @NamedQuery(name = "Member.count", query = "select count(m) from Member m") }
)
public class Member {

    @Id @GeneratedValue
    @Column(name = "member_id")
    private Long id;

    //무조건 값이 있어야하는
//    @NotEmpty
    private String name;

    @Embedded
    private Address address;

    //주문이 아닌 회원정보만 원하기 때문에
    //Member엔티티 json으로 노출할때 주문 정보는 빠지게됨
    //엔티티에 화면을 뿌리기 위한 로직이 들어오면, 프레젠테이션 계층을 위한 로직
    //다른 API스펙을 위한 로직이 들어오면 답이 없음
    //회원과 관련되 조회API가 하나가 아니기 때문에
    @JsonIgnore
    @OneToMany(mappedBy = "member")
    private List<Order> orders = new ArrayList<>();

}
